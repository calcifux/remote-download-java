package com.github.calcifux.remotedownload;

import com.github.calcifux.remotedownload.http.HttpOrigin;

/**
 * Public, framework-agnostic entry point of the library.
 *
 * <p>This class exposes the fluent {@link #from(String)} and {@link #from(DownloadOrigin)}
 * factory methods. The returned {@link RemoteDownloadRequest} is the only object
 * callers need to fetch, forward, or expose remote bytes through any framework
 * (Spring MVC, Quarkus / JAX-RS, plain Servlet, AWS Lambda, command-line tools).
 *
 * <p>Typical usages:
 *
 * <pre>{@code
 * // 1. Public URL
 * RemoteDownload.from("https://cdn.example.com/file.pdf").writeTo(out);
 *
 * // 2. Pre-built source with authentication, headers, retries, ...
 * DownloadOrigin src = HttpOrigin.url(url).bearer(token).build();
 * RemoteDownload.from(src).writeTo(out);
 *
 * // 3. Inspect metadata before consuming the body
 * try (RemoteContent c = RemoteDownload.from(src).fetch()) {
 *     LOG.info("Length={} type={}", c.contentLength().orElse(-1L),
 *              c.contentType().orElse("unknown"));
 *     c.getInputStream().transferTo(out);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class RemoteDownload {

    /** Default chunk size used when copying bytes from source to destination (8 KiB). */
    public static final int DEFAULT_CHUNK_SIZE = 8 * 1024;

    private RemoteDownload() {
        // Utility class — no instances.
    }

    /**
     * Builds a request from a public HTTP / HTTPS URL, delegating to
     * {@link HttpOrigin} with default settings (no headers, no auth, default timeouts).
     *
     * @param url absolute HTTP or HTTPS URL
     * @return a fluent {@link RemoteDownloadRequest} ready to be consumed
     */
    public static RemoteDownloadRequest from(String url) {
        return from(HttpOrigin.url(url).build());
    }

    /**
     * Builds a request from any {@link DownloadOrigin}. Use this overload to
     * supply custom HTTP authentication, cloud storage sources (S3, Azure,
     * GCS), or your own implementations of {@link DownloadOrigin}.
     *
     * @param source the source to read from
     * @return a fluent {@link RemoteDownloadRequest} ready to be consumed
     */
    public static RemoteDownloadRequest from(DownloadOrigin source) {
        return new RemoteDownloadRequest(source);
    }
}
