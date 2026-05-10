package com.github.calcifux.remotedownload.spring.core;

import com.github.calcifux.remotedownload.WriteResult;
import com.github.calcifux.remotedownload.spring.config.RemoteDownloadProperties;
import com.github.calcifux.remotedownload.spring.support.InMemoryOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteDownloadServiceTest {

    private RemoteDownloadProperties properties;
    private RemoteDownloadService service;

    @BeforeEach
    void setUp() {
        properties = new RemoteDownloadProperties();
        properties.setChunkSize(1024);
        properties.setDefaultDisposition("attachment");
        service = new RemoteDownloadService(properties);
    }

    @Test
    void attachmentStreamsBodyAndSetsHeaders() throws Exception {
        var src = new InMemoryOrigin("hello world");

        ResponseEntity<StreamingResponseBody> response = service.attachment(src, "report.pdf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("report.pdf");

        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toString()).isEqualTo("hello world");
    }

    @Test
    void inlineSetsContentDispositionInline() throws Exception {
        var src = new InMemoryOrigin("PDF bytes");

        ResponseEntity<StreamingResponseBody> response = service.inline(src, "preview.pdf");

        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("preview.pdf");

        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toString()).isEqualTo("PDF bytes");
    }

    @Test
    void streamHasNoContentDispositionAndKeepsOctetStream() throws Exception {
        var src = new InMemoryOrigin("forwarded bytes");

        ResponseEntity<StreamingResponseBody> response = service.stream(src);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);

        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toString()).isEqualTo("forwarded bytes");
    }

    @Test
    void attachmentUsesUtf8FilenameEncoding() {
        var src = new InMemoryOrigin("payload");

        ResponseEntity<StreamingResponseBody> response = service.attachment(src, "informe año.pdf");

        String header = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).contains("filename*=UTF-8''");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("informe año.pdf");
    }

    @Test
    void writeToCopiesBytesUsingConfiguredChunkSize() throws Exception {
        var src = new InMemoryOrigin("checksum me");
        properties.setChunkSize(4);
        var out = new ByteArrayOutputStream();

        WriteResult result = service.writeTo(src, out);

        assertThat(out.toString()).isEqualTo("checksum me");
        assertThat(result.getBytesTransferred()).isEqualTo(11L);
    }
}
