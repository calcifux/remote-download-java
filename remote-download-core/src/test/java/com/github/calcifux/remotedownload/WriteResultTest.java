package com.github.calcifux.remotedownload;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WriteResultTest {

    @Test
    void bytesPerSecondComputesThroughput() {
        // 1 MB transferred in 1 second → ≈ 1 048 576 bytes/sec
        WriteResult result = new WriteResult(1_048_576L, Duration.ofSeconds(1), null, null);

        assertThat(result.bytesPerSecond()).isEqualTo(1_048_576L);
    }

    @Test
    void bytesPerSecondHandlesSubMillisecondDurationAsZero() {
        // duration.toMillis() == 0 — division by zero must be guarded.
        WriteResult result = new WriteResult(500L, Duration.ofNanos(123_000), null, null);

        assertThat(result.bytesPerSecond()).isZero();
    }

    @Test
    void bytesPerSecondReturnsZeroWhenDurationIsExactlyZero() {
        WriteResult result = new WriteResult(0L, Duration.ZERO, null, null);

        assertThat(result.bytesPerSecond()).isZero();
    }

    @Test
    void checksumIsEmptyWhenHexIsNull() {
        WriteResult result = new WriteResult(10L, Duration.ofMillis(5), null, null);

        assertThat(result.checksum()).isEmpty();
    }

    @Test
    void checksumExposesHexWhenPresent() {
        WriteResult result = new WriteResult(10L, Duration.ofMillis(5), "SHA-256", "abc123");

        assertThat(result.checksum()).contains("abc123");
        assertThat(result.getChecksumAlgorithm()).isEqualTo("SHA-256");
    }
}
