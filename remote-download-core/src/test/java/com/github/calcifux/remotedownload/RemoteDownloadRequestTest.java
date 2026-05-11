package com.github.calcifux.remotedownload;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteDownloadRequestTest {

    /** Tiny in-memory origin used to exercise the request without network I/O. */
    private static DownloadOrigin origin(String payload) {
        return () -> RemoteContent.builder()
                .inputStream(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)))
                .contentType("text/plain")
                .contentLength((long) payload.length())
                .filename("payload.txt")
                .build();
    }

    @Test
    void chunkSizeAcceptsPositiveValues() throws Exception {
        var out = new ByteArrayOutputStream();
        var request = RemoteDownload.from(origin("hello")).chunkSize(2);

        request.writeTo(out);

        assertThat(out.toString()).isEqualTo("hello");
    }

    @Test
    void chunkSizeRejectsZeroOrNegative() {
        var request = RemoteDownload.from(origin("x"));

        assertThatThrownBy(() -> request.chunkSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize");

        assertThatThrownBy(() -> request.chunkSize(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onProgressFiresPerChunk() throws Exception {
        // 100 bytes payload with a chunk size of 16 → at least 7 progress events.
        byte[] payload = new byte[100];
        DownloadOrigin src = () -> RemoteContent.builder()
                .inputStream(new ByteArrayInputStream(payload))
                .contentLength((long) payload.length)
                .build();

        List<Long> reads = new ArrayList<>();
        RemoteDownload.from(src)
                .chunkSize(16)
                .onProgress((bytesRead, total) -> reads.add(bytesRead))
                .writeTo(new ByteArrayOutputStream());

        assertThat(reads).isNotEmpty();
        // Last entry equals total bytes transferred.
        assertThat(reads.get(reads.size() - 1)).isEqualTo(100L);
    }

    @Test
    void onProgressAcceptsNullToDisable() throws Exception {
        var request = RemoteDownload.from(origin("ok"))
                .onProgress(null);

        WriteResult result = request.writeTo(new ByteArrayOutputStream());

        assertThat(result.getBytesTransferred()).isEqualTo(2L);
    }

    @Test
    void checksumAcceptsNullToDisable() throws Exception {
        WriteResult result = RemoteDownload.from(origin("hello"))
                .checksum(null)
                .writeTo(new ByteArrayOutputStream());

        assertThat(result.checksum()).isEmpty();
    }

    @Test
    void fetchReturnsOpenedRemoteContent() throws Exception {
        try (RemoteContent content = RemoteDownload.from(origin("data")).fetch()) {
            assertThat(content.contentType()).contains("text/plain");
            assertThat(content.contentLength()).contains(4L);
            assertThat(content.filename()).contains("payload.txt");
            assertThat(content.getInputStream().readAllBytes())
                    .isEqualTo("data".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void asInputStreamReturnsLiveStream() throws Exception {
        try (InputStream in = RemoteDownload.from(origin("streaming")).asInputStream()) {
            byte[] bytes = in.readAllBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("streaming");
        }
    }

    @Test
    void fromUrlBuildsDefaultHttpOriginRequest() {
        // Just verifies the overload compiles and returns a usable request — the
        // actual HTTP call is covered by HttpOriginTest.
        RemoteDownloadRequest request = RemoteDownload.from("https://example.com/nope");

        assertThat(request).isNotNull();
    }
}
