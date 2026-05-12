package com.github.calcifux.remotedownload.apache;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteDownloadException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enterprise-grade {@link DownloadOrigin} backed by Apache HttpClient 5.
 *
 * <p>Compared to the JDK-based {@code HttpOrigin}, this implementation adds
 * the capabilities expected by enterprise integrations:
 * <ul>
 *     <li>Automatic retries with configurable count and backoff interval</li>
 *     <li>{@code Authorization: Basic}, {@code Bearer} and {@code NTLM}
 *         authentication schemes (the latter is required by SharePoint and
 *         many Windows-based intranet endpoints)</li>
 *     <li>Authenticated forward proxies</li>
 *     <li>Granular timeouts (connect / response)</li>
 *     <li>Connection pooling provided by Apache HttpClient</li>
 * </ul>
 *
 * <p>Construct instances through the {@link #url(String)} fluent builder:
 *
 * <pre>{@code
 * DownloadOrigin source = ApacheHttpOrigin.url(url)
 *         .ntlm("CORP", "user", "pass")
 *         .proxy("proxy.corp", 8080)
 *         .proxyAuth("proxyUser", "proxyPass")
 *         .retries(3)
 *         .responseTimeout(Duration.ofMinutes(2))
 *         .build();
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
public class ApacheHttpOrigin implements DownloadOrigin {

    private final URI uri;
    private final Map<String, String> headers;
    private final Timeout connectTimeout;
    private final Timeout responseTimeout;
    private final int retries;
    private final TimeValue retryInterval;

    private final HttpHost proxy;
    private final BasicCredentialsProvider credentialsProvider;

    private ApacheHttpOrigin(Builder b) {
        this.uri = b.uri;
        this.headers = Map.copyOf(b.headers);
        this.connectTimeout = b.connectTimeout;
        this.responseTimeout = b.responseTimeout;
        this.retries = b.retries;
        this.retryInterval = b.retryInterval;
        this.proxy = b.proxy;
        this.credentialsProvider = b.credentialsProvider;
    }

    /**
     * Starts a new fluent builder targeting the supplied URL.
     *
     * @param url absolute HTTP or HTTPS URL
     * @return a fluent {@link Builder}
     */
    public static Builder url(String url) {
        return new Builder(URI.create(url));
    }

    /**
     * Issues the configured {@code GET} request through Apache HttpClient and
     * returns the response body wrapped in a {@link RemoteContent}.
     *
     * <p>Both the {@link CloseableHttpResponse} and the underlying
     * {@link CloseableHttpClient} are released when the returned content is
     * closed.
     *
     * @return live remote content; the caller owns the returned object
     * @throws IOException           on network / IO errors
     * @throws RemoteDownloadException on non-success HTTP status
     */
    @Override
    public RemoteContent open() throws IOException {
        log.debug("[ApacheHttpOrigin] GET {}", uri);

        // Connect timeout is configured on the connection manager in modern
        // versions of the Apache HTTP client; response timeout remains on
        // RequestConfig because it is per-request scope.
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(connectTimeout)
                                .build())
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(responseTimeout)
                .build();

        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(retries, retryInterval));

        if (proxy != null) {
            clientBuilder.setProxy(proxy);
        }
        if (credentialsProvider != null) {
            clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        CloseableHttpClient client = clientBuilder.build();
        HttpGet get = new HttpGet(uri);
        headers.forEach(get::addHeader);

        ClassicHttpResponse response;
        try {
            response = client.executeOpen(null, get, null);
        } catch (IOException e) {
            client.close();
            throw e;
        }

        int status = response.getCode();
        if (status >= 400) {
            try (response; client) {
                throw new RemoteDownloadException("HTTP " + status + " when requesting " + uri);
            }
        }

        HttpEntity entity = response.getEntity();
        InputStream body = entity != null ? entity.getContent() : InputStream.nullInputStream();
        String contentType = entity != null && entity.getContentType() != null
                ? entity.getContentType()
                : null;
        Long contentLength = entity != null && entity.getContentLength() >= 0
                ? entity.getContentLength()
                : null;
        String filename = HttpHeaderUtils.parseFilename(
                response.getFirstHeader("Content-Disposition") != null
                        ? response.getFirstHeader("Content-Disposition").getValue()
                        : null
        );

        // When the caller closes the RemoteContent both the HTTP response and
        // the underlying client must be released to free pooled connections.
        // Both closes are best-effort — a failure here cannot surface to the
        // consumer because the body has already been delivered.
        Runnable cleanup = () -> {
            try {
                response.close();
            } catch (IOException e) {
                log.debug("[ApacheHttpOrigin] ignored failure closing response", e);
            }
            try {
                client.close();
            } catch (IOException e) {
                log.debug("[ApacheHttpOrigin] ignored failure closing client", e);
            }
        };

        return RemoteContent.builder()
                .inputStream(body)
                .contentType(contentType)
                .contentLength(contentLength)
                .filename(filename)
                .onClose(cleanup)
                .build();
    }

    // ---------------------------------------------------------------------
    //  Fluent builder
    // ---------------------------------------------------------------------

    /**
     * Fluent builder for {@link ApacheHttpOrigin}. Builders are not thread-safe;
     * create one per source.
     */
    public static class Builder {
        private final URI uri;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Timeout connectTimeout = Timeout.ofSeconds(30);
        private Timeout responseTimeout = Timeout.ofMinutes(5);
        private int retries = 3;
        private TimeValue retryInterval = TimeValue.ofSeconds(2);

        private HttpHost proxy;
        private BasicCredentialsProvider credentialsProvider;

        private Builder(URI uri) {
            this.uri = uri;
        }

        /**
         * Adds or replaces a request header.
         */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Adds an {@code Authorization: Bearer} header. */
        public Builder bearer(String token) {
            return header("Authorization", "Bearer " + token);
        }

        /** Adds an {@code Authorization: Basic} header (UTF-8 + base64). */
        public Builder basicAuth(String user, String pass) {
            String raw = user + ":" + pass;
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            return header("Authorization", "Basic " + encoded);
        }

        /**
         * Configures NTLM authentication for the target host. Required by
         * SharePoint and many Windows intranet endpoints.
         *
         * @param domain Windows domain (workstation)
         * @param user   user principal
         * @param pass   password
         * @return this builder
         */
        @SuppressWarnings("deprecation") // NTCredentials 5-arg ctor needs HC 5.4+; this
                                          // module ships with 5.3.1. Tracked for the
                                          // next dependency bump.
        public Builder ntlm(String domain, String user, String pass) {
            ensureCredentials();
            credentialsProvider.setCredentials(
                    new AuthScope(uri.getHost(), uri.getPort()),
                    new NTCredentials(user, pass.toCharArray(), null, domain)
            );
            return this;
        }

        /**
         * Routes the request through a forward HTTP proxy.
         */
        public Builder proxy(String host, int port) {
            this.proxy = new HttpHost(host, port);
            return this;
        }

        /**
         * Supplies credentials for an authenticated proxy. Must be called
         * after {@link #proxy(String, int)}.
         *
         * @throws IllegalStateException if no proxy has been configured
         */
        public Builder proxyAuth(String user, String pass) {
            if (proxy == null) {
                throw new IllegalStateException("Call proxy(...) before proxyAuth(...)");
            }
            ensureCredentials();
            credentialsProvider.setCredentials(
                    new AuthScope(proxy),
                    new UsernamePasswordCredentials(user, pass.toCharArray())
            );
            return this;
        }

        /** Sets the connect timeout. Defaults to 30 seconds. */
        public Builder connectTimeout(Duration d) {
            this.connectTimeout = Timeout.of(d);
            return this;
        }

        /** Sets the response timeout. Defaults to 5 minutes. */
        public Builder responseTimeout(Duration d) {
            this.responseTimeout = Timeout.of(d);
            return this;
        }

        /** Sets the maximum number of automatic retries. Defaults to 3. */
        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        /** Sets the interval between retries. Defaults to 2 seconds. */
        public Builder retryInterval(Duration d) {
            this.retryInterval = TimeValue.of(d);
            return this;
        }

        /**
         * @return an immutable {@link ApacheHttpOrigin} configured with this builder
         */
        public ApacheHttpOrigin build() {
            return new ApacheHttpOrigin(this);
        }

        private void ensureCredentials() {
            if (credentialsProvider == null) {
                credentialsProvider = new BasicCredentialsProvider();
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Header parsing helpers
    // ---------------------------------------------------------------------

    /**
     * Internal best-effort parser for the {@code Content-Disposition} header.
     * Recognizes both the legacy quoted form and the {@code RFC 5987} encoded
     * form ({@code filename*=UTF-8''...}).
     */
    static final class HttpHeaderUtils {
        private HttpHeaderUtils() {
            // Utility class — no instances.
        }

        static String parseFilename(String contentDisposition) {
            if (contentDisposition == null) return null;
            int idx = contentDisposition.toLowerCase().indexOf("filename");
            if (idx < 0) return null;
            int eq = contentDisposition.indexOf('=', idx);
            if (eq < 0) return null;
            String value = contentDisposition.substring(eq + 1).trim();

            // Cut at the first parameter separator BEFORE touching quotes /
            // RFC 5987 prefix — Content-Disposition can chain extra params
            // after the filename, e.g. `filename="x.pdf"; size=42`.
            int semi = value.indexOf(';');
            if (semi >= 0) value = value.substring(0, semi).trim();

            if (value.startsWith("UTF-8''")) value = value.substring(7);
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
