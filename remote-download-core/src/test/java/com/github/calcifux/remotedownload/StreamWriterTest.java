package com.github.calcifux.remotedownload;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamWriterTest {

    @Test
    void copiesAllBytes() throws Exception {
        byte[] payload = "hello world".getBytes();
        var out = new ByteArrayOutputStream();

        WriteResult result = StreamWriter.copy(
                new ByteArrayInputStream(payload), out, 1024);

        assertThat(result.getBytesTransferred()).isEqualTo(payload.length);
        assertThat(out.toByteArray()).isEqualTo(payload);
        assertThat(result.getDuration()).isNotNull();
    }

    @Test
    void chunksWithCustomBufferSize() throws Exception {
        byte[] payload = new byte[10_000];
        var listener = new RecordingListener();

        StreamWriter.copy(
                new ByteArrayInputStream(payload),
                new ByteArrayOutputStream(),
                1024,
                (long) payload.length,
                listener,
                null);

        // 10000 / 1024 = at least 10 chunks
        assertThat(listener.calls.get()).isGreaterThanOrEqualTo(10);
        assertThat(listener.lastRead.get()).isEqualTo(payload.length);
        assertThat(listener.lastTotal.get()).isEqualTo(payload.length);
    }

    @Test
    void progressTotalNullWhenSizeUnknown() throws Exception {
        var listener = new RecordingListener();
        StreamWriter.copy(
                new ByteArrayInputStream(new byte[10]),
                new ByteArrayOutputStream(),
                1024, null, listener, null);

        assertThat(listener.lastTotal.get()).isNull();
    }

    @Test
    void computesSha256() throws Exception {
        // pre-computed SHA-256 of "hello"
        String expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        WriteResult result = StreamWriter.copy(
                new ByteArrayInputStream("hello".getBytes()),
                new ByteArrayOutputStream(),
                1024, null, null, "SHA-256");

        assertThat(result.getChecksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(result.getChecksumHex()).isEqualTo(expected);
        assertThat(result.checksum()).contains(expected);
    }

    @Test
    void computesMd5() throws Exception {
        // pre-computed MD5 of "hello"
        String expected = "5d41402abc4b2a76b9719d911017c592";

        WriteResult result = StreamWriter.copy(
                new ByteArrayInputStream("hello".getBytes()),
                new ByteArrayOutputStream(),
                1024, null, null, "MD5");

        assertThat(result.getChecksumHex()).isEqualTo(expected);
    }

    @Test
    void checksumNullWhenAlgorithmNotRequested() throws Exception {
        WriteResult result = StreamWriter.copy(
                new ByteArrayInputStream("hello".getBytes()),
                new ByteArrayOutputStream(),
                1024);

        assertThat(result.getChecksumAlgorithm()).isNull();
        assertThat(result.getChecksumHex()).isNull();
        assertThat(result.checksum()).isEmpty();
    }

    @Test
    void throwsOnUnsupportedAlgorithm() {
        assertThatThrownBy(() -> StreamWriter.copy(
                new ByteArrayInputStream(new byte[10]),
                new ByteArrayOutputStream(),
                1024, null, null, "NOSUCH-ALGO"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unsupported digest algorithm");
    }

    @Test
    void emptyStreamProducesZeroBytes() throws Exception {
        WriteResult result = StreamWriter.copy(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                1024);

        assertThat(result.getBytesTransferred()).isZero();
        assertThat(result.bytesPerSecond()).isZero();
    }

    @Test
    void blankAlgorithmIsTreatedAsNoChecksum() throws Exception {
        // Non-null but blank algorithm → short-circuit, no MessageDigest is created.
        WriteResult result = StreamWriter.copy(
                new ByteArrayInputStream("hello".getBytes()),
                new ByteArrayOutputStream(),
                1024, null, null, "   ");

        assertThat(result.getChecksumHex()).isNull();
    }

    // ---------- helpers ----------

    private static class RecordingListener implements ProgressListener {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicLong lastRead = new AtomicLong();
        final java.util.concurrent.atomic.AtomicReference<Long> lastTotal =
                new java.util.concurrent.atomic.AtomicReference<>();

        @Override
        public void onProgress(long bytesRead, Long totalBytes) {
            calls.incrementAndGet();
            lastRead.set(bytesRead);
            lastTotal.set(totalBytes);
        }
    }
}
