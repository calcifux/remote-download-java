package com.github.calcifux.remotedownload.spring;

import com.github.calcifux.remotedownload.spring.support.InMemoryOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadsTest {

    @Test
    void attachmentSetsContentDispositionAndStreamsBody() throws Exception {
        var src = new InMemoryOrigin("hello world");

        ResponseEntity<StreamingResponseBody> response = Downloads.attachment(src, "report.pdf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("report.pdf");

        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out).hasToString("hello world");
    }

    @Test
    void inlineSetsContentDispositionInline() throws Exception {
        var src = new InMemoryOrigin("PDF bytes");

        ResponseEntity<StreamingResponseBody> response = Downloads.inline(src, "preview.pdf");

        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("preview.pdf");

        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out).hasToString("PDF bytes");
    }

    @Test
    void streamHasNoContentDisposition() throws Exception {
        var src = new InMemoryOrigin("raw stream");

        ResponseEntity<StreamingResponseBody> response = Downloads.stream(src);

        // No filename / disposition: header should be missing or empty.
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);

        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out).hasToString("raw stream");
    }

    @Test
    void attachmentSupportsUtf8Filename() {
        var src = new InMemoryOrigin("payload");

        ResponseEntity<StreamingResponseBody> response = Downloads.attachment(src, "reporte año.pdf");

        // RFC 5987 encoded filename* should carry the UTF-8 form.
        String header = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).contains("filename*=UTF-8''");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("reporte año.pdf");
    }

    @Test
    void customChunkSizeStillStreamsCompletely() throws Exception {
        // 32 KiB body — exercise multi-chunk path with a tiny buffer.
        byte[] payload = new byte[32 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        var src = new InMemoryOrigin(payload, "application/octet-stream", null);

        ResponseEntity<StreamingResponseBody> response = Downloads.attachment(src, "blob.bin", 512);

        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        assertThat(out.toByteArray()).isEqualTo(payload);
    }
}
