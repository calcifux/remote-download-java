package com.github.calcifux.remotedownload.quarkus.core;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteDownload;
import com.github.calcifux.remotedownload.StreamWriter;
import com.github.calcifux.remotedownload.WriteResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * CDI-managed service that adapts the universal
 * {@link RemoteDownload} API into JAX-RS
 * {@link Response} values backed by a {@link StreamingOutput}.
 *
 * <p>Although the module is named {@code remote-download-quarkus} the
 * implementation only depends on Jakarta CDI 4 + JAX-RS 3 + MicroProfile
 * Config 3, so it runs unchanged on Quarkus, Helidon, OpenLiberty and any
 * other compatible runtime.
 *
 * <p>Configuration is taken from MicroProfile Config:
 *
 * <pre>{@code
 * remote-download.chunk-size=8192
 * remote-download.default-disposition=attachment
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
@ApplicationScoped
public class RemoteDownloadJaxRsService {

    @Inject
    @ConfigProperty(name = "remote-download.chunk-size", defaultValue = "8192")
    int chunkSize;

    @Inject
    @ConfigProperty(name = "remote-download.default-disposition", defaultValue = "attachment")
    String defaultDisposition;

    /**
     * Builds a response that forces a download by setting
     * {@code Content-Disposition: attachment}.
     */
    public Response attachment(DownloadOrigin source, String filename) {
        return responseFor(source, filename, "attachment");
    }

    /**
     * Builds a response that hints the browser to render the resource inline
     * by setting {@code Content-Disposition: inline}.
     */
    public Response inline(DownloadOrigin source, String filename) {
        return responseFor(source, filename, "inline");
    }

    /**
     * Builds a response without a {@code Content-Disposition} header.
     */
    public Response stream(DownloadOrigin source) {
        return responseFor(source, null, null);
    }

    /**
     * Internal helper that assembles the JAX-RS response with a
     * {@link StreamingOutput} body.
     */
    private Response responseFor(DownloadOrigin source, String filename, String disposition) {
        StreamingOutput out = os -> {
            try (RemoteContent content = source.open()) {
                long bytes = StreamWriter.copy(content.getInputStream(), os, chunkSize).getBytesTransferred();
                log.debug("[RemoteDownloadJaxRsService] streamed {} bytes filename={}", bytes, filename);
            } catch (IOException e) {
                log.error("[RemoteDownloadJaxRsService] failed to stream filename={}: {}", filename, e.getMessage(), e);
                throw e;
            }
        };

        Response.ResponseBuilder rb = Response.ok(out)
                .type(MediaType.APPLICATION_OCTET_STREAM);

        String dispo = Optional.ofNullable(disposition).orElse(defaultDisposition);
        if (filename != null && dispo != null) {
            String encoded = encodeFilename(filename);
            rb.header("Content-Disposition",
                    dispo + "; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        }

        return rb.build();
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
        return RemoteDownload.from(source).chunkSize(chunkSize).writeTo(out);
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
