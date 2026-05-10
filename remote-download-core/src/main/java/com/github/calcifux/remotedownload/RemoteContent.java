package com.github.calcifux.remotedownload;

import lombok.Builder;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Wrapper for an opened remote resource. Couples the live {@link InputStream}
 * with the metadata the source was able to resolve (content type, length,
 * suggested filename) and provides a single point to release every underlying
 * resource through {@link AutoCloseable#close()}.
 *
 * <p>Intended to be consumed with try-with-resources:
 *
 * <pre>{@code
 * try (RemoteContent content = source.open()) {
 *     content.getInputStream().transferTo(out);
 * }
 * }</pre>
 *
 * <p>Closing this object closes the {@link InputStream} and runs the optional
 * {@link #onClose} hook, which providers use to dispose of additional resources
 * such as SDK clients or HTTP responses.
 *
 * @since 1.0.0
 */
@Getter
@Builder
public class RemoteContent implements AutoCloseable {

    /**
     * Live input stream of the remote resource. Closing this stream — directly
     * or through {@link #close()} — releases the underlying connection.
     */
    private final InputStream inputStream;

    /** MIME type advertised by the source, or {@code null} when not available. */
    private final String contentType;

    /** Content length in bytes, or {@code null} when the source cannot determine it. */
    private final Long contentLength;

    /**
     * Suggested filename. Derived from the {@code Content-Disposition} header for
     * HTTP sources, or from the object key / blob name for cloud sources.
     */
    private final String filename;

    /**
     * Optional cleanup hook executed by {@link #close()} after the stream has
     * been closed. Providers use this to release additional resources such as
     * the underlying HTTP client, an S3 response, an Azure connection, etc.
     */
    private final Runnable onClose;

    /**
     * @return the content type if the source advertised one
     */
    public Optional<String> contentType() {
        return Optional.ofNullable(contentType);
    }

    /**
     * @return the content length in bytes if the source advertised one
     */
    public Optional<Long> contentLength() {
        return Optional.ofNullable(contentLength);
    }

    /**
     * @return the suggested filename if the source advertised one
     */
    public Optional<String> filename() {
        return Optional.ofNullable(filename);
    }

    /**
     * Closes the underlying input stream and runs the cleanup hook, if any.
     *
     * <p>Failures inside the cleanup hook are swallowed by design — the contract
     * of {@link AutoCloseable} is best-effort cleanup and the input stream takes
     * precedence.
     *
     * @throws IOException if closing the input stream fails
     */
    @Override
    public void close() throws IOException {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } finally {
            if (onClose != null) {
                try {
                    onClose.run();
                } catch (RuntimeException ignored) {
                    // Best-effort cleanup; primary close already succeeded.
                }
            }
        }
    }
}
