package com.github.calcifux.remotedownload.spring.core;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteDownload;
import com.github.calcifux.remotedownload.StreamWriter;
import com.github.calcifux.remotedownload.WriteResult;
import com.github.calcifux.remotedownload.spring.config.RemoteDownloadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Spring MVC-aware service that adapts the universal
 * {@link RemoteDownload} API into ready-to-return
 * {@code ResponseEntity<StreamingResponseBody>} values.
 *
 * <p>This is the bean that callers inject when they prefer constructor
 * injection over the static {@code RemoteDownloadFacade}. Both forms share the
 * same configuration loaded from {@link RemoteDownloadProperties}.
 *
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteDownloadService {

    private final RemoteDownloadProperties properties;

    /**
     * Builds a response that forces a download by setting
     * {@code Content-Disposition: attachment} with the supplied filename.
     *
     * @param source   the remote source to stream
     * @param filename the suggested filename for the client
     * @return response entity wrapping a streaming body
     */
    public ResponseEntity<StreamingResponseBody> attachment(DownloadOrigin source, String filename) {
        return responseFor(source, filename, "attachment");
    }

    /**
     * Builds a response that hints the browser to render the resource inline
     * by setting {@code Content-Disposition: inline}.
     *
     * @param source   the remote source to stream
     * @param filename the suggested filename for the client
     * @return response entity wrapping a streaming body
     */
    public ResponseEntity<StreamingResponseBody> inline(DownloadOrigin source, String filename) {
        return responseFor(source, filename, "inline");
    }

    /**
     * Builds a response without a {@code Content-Disposition} header. Useful
     * when forwarding the body to an internal consumer that does not need
     * download semantics.
     *
     * @param source the remote source to stream
     * @return response entity wrapping a streaming body
     */
    public ResponseEntity<StreamingResponseBody> stream(DownloadOrigin source) {
        return responseFor(source, null, null);
    }

    /**
     * Internal helper that assembles the response headers and the streaming body.
     */
    private ResponseEntity<StreamingResponseBody> responseFor(DownloadOrigin source,
                                                              String filename,
                                                              String disposition) {
        HttpHeaders headers = new HttpHeaders();

        String dispo = disposition != null ? disposition : properties.getDefaultDisposition();
        if (filename != null && dispo != null) {
            headers.setContentDisposition(
                    ContentDisposition.builder(dispo)
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
            );
        }

        StreamingResponseBody body = out -> {
            try (RemoteContent content = source.open()) {
                long bytes = StreamWriter.copy(content.getInputStream(), out, properties.getChunkSize()).getBytesTransferred();
                log.debug("[RemoteDownloadService] streamed {} bytes filename={}", bytes, filename);
            } catch (IOException e) {
                log.error("[RemoteDownloadService] failed to stream filename={}: {}", filename, e.getMessage(), e);
                throw e;
            }
        };

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(defaultContentType())
                .body(body);
    }

    /**
     * Default content type for downloads. Kept as
     * {@code application/octet-stream} to avoid issuing a second open() just
     * to peek at metadata; downstream callers can override the type when they
     * already know it.
     */
    private static MediaType defaultContentType() {
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Convenience overload that copies a source into any
     * {@link OutputStream} using the configured chunk size.
     *
     * @param source the remote source to stream
     * @param out    destination stream (left open)
     * @return aggregated transfer result (bytes, duration, optional checksum)
     * @throws IOException if the transfer fails
     */
    public WriteResult writeTo(DownloadOrigin source, OutputStream out) throws IOException {
        return RemoteDownload.from(source)
                .chunkSize(properties.getChunkSize())
                .writeTo(out);
    }
}
