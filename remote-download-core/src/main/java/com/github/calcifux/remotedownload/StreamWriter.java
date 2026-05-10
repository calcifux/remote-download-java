package com.github.calcifux.remotedownload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

/**
 * Internal helper that copies bytes from an {@link InputStream} to an
 * {@link OutputStream} in fixed-size chunks, flushing after every write so the
 * destination (typically an HTTP response) starts emitting bytes as they arrive
 * from the origin instead of waiting for the full payload.
 *
 * <p>Optionally computes a digest of the bytes flowing through the pipe and
 * fires a {@link ProgressListener} after each chunk.
 *
 * <p>Neither stream is closed by this class — that responsibility belongs to
 * the caller, who controls the lifecycle of both ends.
 *
 * @since 1.0.0
 */
public final class StreamWriter {

    private StreamWriter() {
        // Utility class — no instances.
    }

    /**
     * Convenience overload that copies without progress reporting or checksum.
     *
     * @param in        source stream (left open)
     * @param out       destination stream (left open)
     * @param chunkSize size of the in-memory buffer used for each I/O cycle
     * @return result aggregating bytes transferred and elapsed duration
     * @throws IOException if either stream raises an I/O error during the copy
     */
    public static WriteResult copy(InputStream in, OutputStream out, int chunkSize) throws IOException {
        return copy(in, out, chunkSize, null, null, null);
    }

    /**
     * Copies the full contents of {@code in} into {@code out} using a buffer of
     * {@code chunkSize} bytes. Flushes the destination after every chunk so the
     * payload is forwarded incrementally.
     *
     * <p>When {@code digestAlgorithm} is non-null the input stream is wrapped in
     * a {@link DigestInputStream}, and the resulting hex digest is exposed in
     * the returned {@link WriteResult}. When {@code progressListener} is
     * non-null it is invoked once per chunk with the running byte counter.
     *
     * @param in                 source stream (left open)
     * @param out                destination stream (left open)
     * @param chunkSize          size of the in-memory buffer used for each I/O cycle
     * @param totalBytes         expected total size, or {@code null} if unknown
     * @param progressListener   optional progress callback; may be {@code null}
     * @param digestAlgorithm    optional digest algorithm name (for example
     *                           {@code "SHA-256"} or {@code "MD5"}); may be {@code null}
     * @return aggregated {@link WriteResult}
     * @throws IOException if either stream raises an I/O error during the copy
     *                     or the requested digest algorithm is unsupported
     */
    public static WriteResult copy(InputStream in,
                                   OutputStream out,
                                   int chunkSize,
                                   Long totalBytes,
                                   ProgressListener progressListener,
                                   String digestAlgorithm) throws IOException {

        InputStream src = in;
        MessageDigest digest = null;
        if (digestAlgorithm != null && !digestAlgorithm.isBlank()) {
            try {
                digest = MessageDigest.getInstance(digestAlgorithm);
                src = new DigestInputStream(in, digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("Unsupported digest algorithm: " + digestAlgorithm, e);
            }
        }

        Instant start = Instant.now();
        byte[] buf = new byte[chunkSize];
        long total = 0;
        int n;
        while ((n = src.read(buf)) != -1) {
            out.write(buf, 0, n);
            out.flush();
            total += n;
            if (progressListener != null) {
                progressListener.onProgress(total, totalBytes);
            }
        }
        Duration elapsed = Duration.between(start, Instant.now());

        String hex = (digest != null) ? bytesToHex(digest.digest()) : null;
        return new WriteResult(total, elapsed, digestAlgorithm, hex);
    }

    /** Lower-case hex encoding of an arbitrary byte array. */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
