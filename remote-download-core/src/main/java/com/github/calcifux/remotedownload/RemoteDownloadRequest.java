package com.github.calcifux.remotedownload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Fluent request that wraps a {@link DownloadOrigin} and exposes the three
 * canonical ways to consume its content:
 *
 * <ul>
 *     <li>{@link #writeTo(OutputStream)} — copies the entire payload to the
 *         supplied stream and releases every resource. Covers the vast majority
 *         of use cases (HTTP responses, file output, pipes).</li>
 *     <li>{@link #fetch()} — returns the {@link RemoteContent} together with
 *         the resolved metadata; the caller is responsible for closing it.</li>
 *     <li>{@link #asInputStream()} — returns only the live {@link InputStream};
 *         the caller is responsible for closing it.</li>
 * </ul>
 *
 * <p>Optional decorators apply to {@link #writeTo(OutputStream)}:
 *
 * <ul>
 *     <li>{@link #chunkSize(int)} — buffer size used by the copy loop</li>
 *     <li>{@link #onProgress(ProgressListener)} — callback fired per chunk</li>
 *     <li>{@link #checksum(String)} — digest algorithm to compute on the bytes</li>
 * </ul>
 *
 * <p>Instances are not reusable across multiple calls because the underlying
 * source is opened on demand. Build a new request per consumption.
 *
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteDownloadRequest {

    private final DownloadOrigin source;
    private int chunkSize = RemoteDownload.DEFAULT_CHUNK_SIZE;
    private ProgressListener progressListener;
    private String checksumAlgorithm;

    /**
     * Overrides the buffer size used when copying bytes from the source to the
     * destination stream. Higher values reduce the number of I/O syscalls but
     * increase memory pressure per concurrent transfer.
     *
     * @param chunkSize buffer size in bytes; must be greater than zero
     * @return this request, for chaining
     * @throws IllegalArgumentException if {@code chunkSize <= 0}
     */
    public RemoteDownloadRequest chunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than zero");
        }
        this.chunkSize = chunkSize;
        return this;
    }

    /**
     * Registers a progress callback that is invoked once per chunk during the
     * copy. The total size argument may be {@code null} when the origin did
     * not advertise a {@code Content-Length}.
     *
     * @param listener progress listener; pass {@code null} to disable
     * @return this request, for chaining
     */
    public RemoteDownloadRequest onProgress(ProgressListener listener) {
        this.progressListener = listener;
        return this;
    }

    /**
     * Computes a checksum of the bytes flowing through the pipe using the
     * supplied algorithm and exposes the hex digest in the resulting
     * {@link WriteResult}. Common values are {@code "MD5"}, {@code "SHA-1"} and
     * {@code "SHA-256"}.
     *
     * <p>The algorithm name is resolved through {@link java.security.MessageDigest},
     * so any provider-supplied algorithm is acceptable.
     *
     * @param algorithm algorithm name; pass {@code null} to disable
     * @return this request, for chaining
     */
    public RemoteDownloadRequest checksum(String algorithm) {
        this.checksumAlgorithm = algorithm;
        return this;
    }

    /**
     * Opens the source, copies its full content to {@code out}, and releases
     * every underlying resource (network connection, SDK client, file handle).
     *
     * <p>This is the recommended path for the common case of forwarding bytes
     * from a remote location to an HTTP response or a file.
     *
     * @param out the destination output stream
     * @return aggregated transfer result (bytes, duration, optional checksum)
     * @throws IOException if the source cannot be opened or the transfer fails
     */
    public WriteResult writeTo(OutputStream out) throws IOException {
        try (RemoteContent content = source.open()) {
            WriteResult result = StreamWriter.copy(
                    content.getInputStream(),
                    out,
                    chunkSize,
                    content.getContentLength(),
                    progressListener,
                    checksumAlgorithm
            );
            log.debug("[RemoteDownloadRequest] writeTo completed; bytes={} duration={} checksum={}",
                    result.getBytesTransferred(),
                    result.getDuration(),
                    result.getChecksumHex());
            return result;
        }
    }

    /**
     * Opens the source and returns the {@link RemoteContent} so the caller can
     * read the metadata before consuming the body. The returned object exposes
     * a live input stream and <strong>must</strong> be closed by the caller.
     *
     * @return the opened remote content
     * @throws IOException if the source cannot be opened
     */
    public RemoteContent fetch() throws IOException {
        return source.open();
    }

    /**
     * Opens the source and returns only the underlying {@link InputStream}.
     *
     * <p>The caller forgoes access to metadata (content type, length, filename)
     * and must close the returned stream — closing the stream releases the
     * underlying connection and any associated resources.
     *
     * @return the live input stream of the remote content
     * @throws IOException if the source cannot be opened
     */
    public InputStream asInputStream() throws IOException {
        return source.open().getInputStream();
    }
}
