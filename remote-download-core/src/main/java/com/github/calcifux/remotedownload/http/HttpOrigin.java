package com.github.calcifux.remotedownload.http;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteDownloadException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight {@link DownloadOrigin} backed by the JDK's native
 * {@link java.net.http.HttpClient}. Suitable for the majority of public,
 * non-enterprise scenarios.
 *
 * <p>Supports:
 * <ul>
 *     <li>{@code GET} requests against any HTTP / HTTPS URL</li>
 *     <li>Custom request headers</li>
 *     <li>{@code Authorization: Basic} authentication</li>
 *     <li>{@code Authorization: Bearer} authentication</li>
 *     <li>Independent connect and request timeouts</li>
 *     <li>HTTP/2 negotiation (transparent, courtesy of the JDK)</li>
 * </ul>
 *
 * <p>For enterprise scenarios that require automatic retries, NTLM or Kerberos
 * authentication, authenticated proxies, or fine-grained connection pooling,
 * use {@code remote-download-apache} and its {@code ApacheHttpOrigin} instead.
 *
 * <p>Construct instances through the {@link #url(String)} fluent builder:
 *
 * <pre>{@code
 * DownloadOrigin source = HttpOrigin.url("https://api.example.com/files/123")
 *         .header("X-Tenant", "acme")
 *         .bearer(token)
 *         .connectTimeout(Duration.ofSeconds(10))
 *         .build();
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
public class HttpOrigin implements DownloadOrigin {

    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?",
            Pattern.CASE_INSENSITIVE
    );

    private final URI uri;
    private final Map<String, String> headers;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    private HttpOrigin(Builder b) {
        this.uri = b.uri;
        this.headers = Map.copyOf(b.headers);
        this.connectTimeout = b.connectTimeout;
        this.requestTimeout = b.requestTimeout;
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
     * Issues the configured HTTP {@code GET} request and returns the live
     * response body wrapped in a {@link RemoteContent}.
     *
     * <p>The response is validated synchronously: any 4xx or 5xx status causes
     * a {@link RemoteDownloadException} to be thrown after closing the body.
     *
     * @return live remote content; the caller owns the returned object
     * @throws IOException             on network / IO errors
     * @throws RemoteDownloadException   on non-success HTTP status or interruption
     */
    @Override
    public RemoteContent open() throws IOException {
        log.debug("[HttpOrigin] GET {}", uri);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(requestTimeout);
        headers.forEach(req::header);

        HttpResponse<InputStream> response;
        try {
            response = client.send(req.build(), BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteDownloadException("Interrupted while requesting " + uri, e);
        }

        int status = response.statusCode();
        if (status >= 400) {
            try (InputStream body = response.body()) {
                // Drain ignored to release the underlying connection.
            }
            throw new RemoteDownloadException("HTTP " + status + " when requesting " + uri);
        }

        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        Long contentLength = response.headers().firstValueAsLong("Content-Length")
                .stream().boxed().findFirst().orElse(null);
        String filename = parseFilename(
                response.headers().firstValue("Content-Disposition").orElse(null)
        );

        return RemoteContent.builder()
                .inputStream(response.body())
                .contentType(contentType)
                .contentLength(contentLength)
                .filename(filename)
                .build();
    }

    /**
     * Best-effort extraction of the {@code filename} parameter from a
     * {@code Content-Disposition} header value. Handles plain and
     * {@code RFC 5987}-style ({@code filename*=UTF-8''...}) values.
     *
     * @param contentDisposition raw header value, may be {@code null}
     * @return the parsed filename, or {@code null} if none could be extracted
     */
    static String parseFilename(String contentDisposition) {
        if (contentDisposition == null) {
            return null;
        }
        Matcher m = FILENAME_PATTERN.matcher(contentDisposition);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    // ---------------------------------------------------------------------
    //  Fluent builder
    // ---------------------------------------------------------------------

    /**
     * Fluent builder for {@link HttpOrigin}. Builders are not thread-safe;
     * create one per source.
     */
    public static class Builder {
        private final URI uri;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration requestTimeout = Duration.ofMinutes(5);

        private Builder(URI uri) {
            this.uri = uri;
        }

        /**
         * Adds or replaces a request header.
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /**
         * Adds an {@code Authorization: Bearer <token>} header.
         *
         * @param token opaque bearer token (no quoting required)
         * @return this builder
         */
        public Builder bearer(String token) {
            return header("Authorization", "Bearer " + token);
        }

        /**
         * Adds an {@code Authorization: Basic <base64(user:pass)>} header.
         *
         * @param user user name
         * @param pass password (UTF-8 encoded before base64 encoding)
         * @return this builder
         */
        public Builder basicAuth(String user, String pass) {
            String raw = user + ":" + pass;
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            return header("Authorization", "Basic " + encoded);
        }

        /**
         * Sets the maximum time allowed for the TCP / TLS connection to be
         * established. Defaults to 30 seconds.
         *
         * @param d connect timeout
         * @return this builder
         */
        public Builder connectTimeout(Duration d) {
            this.connectTimeout = d;
            return this;
        }

        /**
         * Sets the maximum time allowed for the full request (connect + headers
         * + body) to complete. Defaults to 5 minutes.
         *
         * @param d request timeout
         * @return this builder
         */
        public Builder requestTimeout(Duration d) {
            this.requestTimeout = d;
            return this;
        }

        /**
         * @return an immutable {@link HttpOrigin} configured with this builder
         */
        public HttpOrigin build() {
            return new HttpOrigin(this);
        }
    }
}
