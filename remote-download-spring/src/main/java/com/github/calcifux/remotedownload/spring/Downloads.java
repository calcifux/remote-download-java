package com.github.calcifux.remotedownload.spring;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteDownload;
import com.github.calcifux.remotedownload.StreamWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Static factory for Spring MVC {@link ResponseEntity} values that stream a
 * {@link DownloadOrigin} directly to the browser without storing the payload on
 * the server's filesystem.
 *
 * <p>Mirrors the ergonomics of Laravel's {@code response()->streamDownload(...)}
 * and {@code response()->file(...)} helpers and complements the injectable
 * {@link com.github.calcifux.remotedownload.spring.core.RemoteDownloadService} that
 * the auto-configuration registers.
 *
 * <p>Choose between the two flavours per project taste:
 *
 * <pre>{@code
 * // 1) Static factory (Laravel-style ergonomics, default chunk size)
 * return Downloads.attachment(src, "report.pdf");
 *
 * // 2) Injected bean (idiomatic Spring, chunk size driven by application.yml)
 * @RequiredArgsConstructor
 * class FilesController {
 *     private final RemoteDownloadService streamer;
 *     ResponseEntity<StreamingResponseBody> download() {
 *         return streamer.attachment(src, "report.pdf");
 *     }
 * }
 * }</pre>
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
    public static ResponseEntity<StreamingResponseBody> attachment(DownloadOrigin source, String filename) {
        return attachment(source, filename, RemoteDownload.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Builds a response with {@code Content-Disposition: attachment} and an
     * explicit chunk size.
     */
    public static ResponseEntity<StreamingResponseBody> attachment(DownloadOrigin source, String filename, int chunkSize) {
        return build(source, filename, "attachment", chunkSize);
    }

    // ---------------------------------------------------------------------
    //  inline — preview in the browser when supported
    // ---------------------------------------------------------------------

    /**
     * Builds a response with {@code Content-Disposition: inline} and the
     * default chunk size ({@link RemoteDownload#DEFAULT_CHUNK_SIZE}).
     */
    public static ResponseEntity<StreamingResponseBody> inline(DownloadOrigin source, String filename) {
        return inline(source, filename, RemoteDownload.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Builds a response with {@code Content-Disposition: inline} and an
     * explicit chunk size.
     */
    public static ResponseEntity<StreamingResponseBody> inline(DownloadOrigin source, String filename, int chunkSize) {
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
    public static ResponseEntity<StreamingResponseBody> stream(DownloadOrigin source) {
        return stream(source, RemoteDownload.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Builds a response without a {@code Content-Disposition} header and an
     * explicit chunk size.
     */
    public static ResponseEntity<StreamingResponseBody> stream(DownloadOrigin source, int chunkSize) {
        return build(source, null, null, chunkSize);
    }

    // ---------------------------------------------------------------------
    //  Internal builder
    // ---------------------------------------------------------------------

    private static ResponseEntity<StreamingResponseBody> build(DownloadOrigin source,
                                                               String filename,
                                                               String disposition,
                                                               int chunkSize) {
        HttpHeaders headers = new HttpHeaders();

        if (filename != null && disposition != null) {
            headers.setContentDisposition(
                    ContentDisposition.builder(disposition)
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
            );
        }

        StreamingResponseBody body = out -> {
            try (RemoteContent content = source.open()) {
                long bytes = StreamWriter.copy(content.getInputStream(), out, chunkSize).getBytesTransferred();
                log.debug("[Downloads] streamed {} bytes filename={}", bytes, filename);
            } catch (IOException e) {
                log.error("[Downloads] failed to stream filename={}: {}", filename, e.getMessage(), e);
                throw e;
            }
        };

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
