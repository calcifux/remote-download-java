package com.github.calcifux.remotedownload.quarkus;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteDownload;
import com.github.calcifux.remotedownload.StreamWriter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Static factory for JAX-RS {@link Response} values that stream a
 * {@link DownloadOrigin} directly to the browser without storing the payload on
 * the server's filesystem.
 *
 * <p>Mirrors the ergonomics of Laravel's {@code response()->streamDownload(...)}
 * and {@code response()->file(...)} helpers and complements the injectable
 * {@link com.github.calcifux.remotedownload.quarkus.core.RemoteDownloadJaxRsService}
 * available in any CDI runtime.
 *
 * <p>Choose between the two flavours per project taste:
 *
 * <pre>{@code
 * // 1) Static factory (Laravel-style ergonomics, default chunk size)
 * return Downloads.attachment(src, "report.pdf");
 *
 * // 2) Injected bean (idiomatic CDI, chunk size driven by application.properties)
 * @Inject RemoteDownloadJaxRsService streamer;
 * Response download() {
 *     return streamer.attachment(src, "report.pdf");
 * }
 * }</pre>
 *
 * <p>The implementation only depends on Jakarta JAX-RS, so it works unchanged
 * on Quarkus, Helidon, OpenLiberty and any compatible runtime.
 *
 * @since 1.0.0
 */
@Slf4j
public final class Downloads {

    private Downloads() {
        // Utility class — no instances.
    }

    // ---------------------------------------------------------------------
    //  attachment — forces the browser to download
    // ---------------------------------------------------------------------

    /**
     * Builds a response with {@code Content-Disposition: attachment} and the
     * default chunk size ({@link RemoteDownload#DEFAULT_CHUNK_SIZE}).
     */
    public static Response attachment(DownloadOrigin source, String filename) {
        return attachment(source, filename, RemoteDownload.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Builds a response with {@code Content-Disposition: attachment} and an
     * explicit chunk size.
     */
    public static Response attachment(DownloadOrigin source, String filename, int chunkSize) {
        return build(source, filename, "attachment", chunkSize);
    }

    // ---------------------------------------------------------------------
    //  inline — preview in the browser when supported
    // ---------------------------------------------------------------------

    /**
     * Builds a response with {@code Content-Disposition: inline} and the
     * default chunk size ({@link RemoteDownload#DEFAULT_CHUNK_SIZE}).
     */
    public static Response inline(DownloadOrigin source, String filename) {
        return inline(source, filename, RemoteDownload.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Builds a response with {@code Content-Disposition: inline} and an
     * explicit chunk size.
     */
    public static Response inline(DownloadOrigin source, String filename, int chunkSize) {
        return build(source, filename, "inline", chunkSize);
    }

    // ---------------------------------------------------------------------
    //  stream — no Content-Disposition (forwarding scenarios)
    // ---------------------------------------------------------------------

    /**
     * Builds a response without a {@code Content-Disposition} header — useful
     * when forwarding bytes between internal services that decide framing
     * elsewhere.
     */
    public static Response stream(DownloadOrigin source) {
        return stream(source, RemoteDownload.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Builds a response without a {@code Content-Disposition} header and an
     * explicit chunk size.
     */
    public static Response stream(DownloadOrigin source, int chunkSize) {
        return build(source, null, null, chunkSize);
    }

    // ---------------------------------------------------------------------
    //  Internal builder
    // ---------------------------------------------------------------------

    private static Response build(DownloadOrigin source,
                                  String filename,
                                  String disposition,
                                  int chunkSize) {

        StreamingOutput body = out -> {
            try (RemoteContent content = source.open()) {
                long bytes = StreamWriter.copy(content.getInputStream(), out, chunkSize).getBytesTransferred();
                log.debug("[Downloads] streamed {} bytes filename={}", bytes, filename);
            } catch (IOException e) {
                log.error("[Downloads] failed to stream filename={}: {}", filename, e.getMessage(), e);
                throw e;
            }
        };

        Response.ResponseBuilder rb = Response.ok(body)
                .type(MediaType.APPLICATION_OCTET_STREAM);

        if (filename != null && disposition != null) {
            String encoded = encodeFilename(filename);
            rb.header("Content-Disposition",
                    disposition + "; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        }

        return rb.build();
    }

    /**
     * RFC 5987 percent-encoding for the {@code filename*} parameter. Delegates
     * to {@link URLEncoder}; the only required twist is that {@code URLEncoder}
     * encodes a space as {@code +} (form-encoded), while RFC 5987 expects
     * {@code %20}.
     */
    private static String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
