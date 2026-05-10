package com.github.calcifux.remotedownload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.util.Optional;

/**
 * Outcome of a {@link RemoteDownloadRequest#writeTo(java.io.OutputStream)}
 * invocation. Aggregates the basic numeric stats together with an optional
 * checksum digest of the bytes that flowed through the pipe.
 *
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public class WriteResult {

    /** Total number of bytes copied from origin to destination. */
    private final long bytesTransferred;

    /** Wall-clock time spent on the copy phase (excluding origin authentication). */
    private final Duration duration;

    /**
     * Algorithm identifier of the computed checksum (for example
     * {@code "SHA-256"}, {@code "MD5"}), or {@code null} when no checksum was
     * requested through {@link RemoteDownloadRequest#checksum(String)}.
     */
    private final String checksumAlgorithm;

    /**
     * Hex representation of the checksum digest, or {@code null} when no
     * checksum was requested.
     */
    private final String checksumHex;

    /**
     * @return the checksum digest as hex if one was computed
     */
    public Optional<String> checksum() {
        return Optional.ofNullable(checksumHex);
    }

    /**
     * @return effective throughput in bytes per second; zero if duration is zero
     */
    public long bytesPerSecond() {
        long millis = duration.toMillis();
        if (millis <= 0) return 0L;
        return (bytesTransferred * 1000L) / millis;
    }
}
