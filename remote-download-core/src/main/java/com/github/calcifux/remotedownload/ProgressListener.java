package com.github.calcifux.remotedownload;

/**
 * Callback invoked on every chunk written to the destination during a transfer.
 *
 * <p>Useful for surfacing progress to logs, metrics or live UIs (Server-Sent
 * Events, WebSocket). The total size may be {@code null} when the origin did
 * not advertise a {@code Content-Length} header (chunked HTTP responses, some
 * SFTP servers, etc.).
 *
 * <p>Wire it through the fluent API:
 *
 * <pre>{@code
 * RemoteDownload.from(source)
 *     .onProgress((read, total) -> {
 *         long pct = total != null ? (read * 100L / total) : -1L;
 *         log.info("downloaded {} / {} bytes ({}%)", read, total, pct);
 *     })
 *     .writeTo(out);
 * }</pre>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * Invoked after each chunk is written.
     *
     * @param bytesRead  total bytes copied so far
     * @param totalBytes total bytes expected, or {@code null} when unknown
     */
    void onProgress(long bytesRead, Long totalBytes);
}
