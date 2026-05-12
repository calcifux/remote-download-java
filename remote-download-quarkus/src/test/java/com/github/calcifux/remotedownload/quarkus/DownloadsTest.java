package com.github.calcifux.remotedownload.quarkus;

import com.github.calcifux.remotedownload.quarkus.support.InMemoryOrigin;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadsTest {

    @Test
    void attachmentSetsContentDispositionAndStreamsBody() throws Exception {
        var src = new InMemoryOrigin("hello world");

        Response response = Downloads.attachment(src, "report.pdf");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType()).hasToString(MediaType.APPLICATION_OCTET_STREAM);
        String disposition = response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .startsWith("attachment;")
                .contains("filename=\"report.pdf\"")
                .contains("filename*=UTF-8''report.pdf");

        var out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        assertThat(out).hasToString("hello world");
    }

    @Test
    void inlineSetsContentDispositionInline() throws Exception {
        var src = new InMemoryOrigin("PDF bytes");

        Response response = Downloads.inline(src, "preview.pdf");

        String disposition = response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .startsWith("inline;")
                .contains("filename=\"preview.pdf\"");

        var out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        assertThat(out).hasToString("PDF bytes");
    }

    @Test
    void streamHasNoContentDisposition() throws Exception {
        var src = new InMemoryOrigin("raw stream");

        Response response = Downloads.stream(src);

        assertThat(response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(response.getMediaType()).hasToString(MediaType.APPLICATION_OCTET_STREAM);

        var out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        assertThat(out).hasToString("raw stream");
    }

    @Test
    void attachmentEncodesUtf8FilenamePerRfc5987() {
        var src = new InMemoryOrigin("payload");

        // "año" → a, %C3%B1, o ; space → %20
        Response response = Downloads.attachment(src, "reporte año.pdf");

        String disposition = response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .contains("filename=\"reporte año.pdf\"")
                .contains("filename*=UTF-8''reporte%20a%C3%B1o.pdf");
    }

    @Test
    void customChunkSizeStillStreamsCompletely() throws Exception {
        byte[] payload = new byte[32 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        var src = new InMemoryOrigin(payload, "application/octet-stream", null);

        Response response = Downloads.attachment(src, "blob.bin", 512);

        var out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);

        assertThat(out.toByteArray()).isEqualTo(payload);
    }
}
