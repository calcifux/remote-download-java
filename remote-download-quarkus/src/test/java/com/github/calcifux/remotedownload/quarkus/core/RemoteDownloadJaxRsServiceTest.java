package com.github.calcifux.remotedownload.quarkus.core;

import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.WriteResult;
import com.github.calcifux.remotedownload.quarkus.support.InMemoryOrigin;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteDownloadJaxRsServiceTest {

    private RemoteDownloadJaxRsService service;

    @BeforeEach
    void setUp() {
        // Direct instantiation — bypass CDI; package-private fields are visible
        // because this test lives in the same package as the service.
        service = new RemoteDownloadJaxRsService();
        service.chunkSize = 1024;
        service.defaultDisposition = "attachment";
    }

    @Test
    void attachmentStreamsBodyAndSetsHeaders() throws Exception {
        var src = new InMemoryOrigin("hello world");

        Response response = service.attachment(src, "report.pdf");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType()).hasToString(MediaType.APPLICATION_OCTET_STREAM);
        String disposition = response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .startsWith("attachment;")
                .contains("filename=\"report.pdf\"");

        var out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        assertThat(out).hasToString("hello world");
    }

    @Test
    void inlineSetsContentDispositionInline() throws Exception {
        var src = new InMemoryOrigin("PDF bytes");

        Response response = service.inline(src, "preview.pdf");

        String disposition = response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .startsWith("inline;")
                .contains("filename=\"preview.pdf\"");

        var out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        assertThat(out).hasToString("PDF bytes");
    }

    @Test
    void streamHasNoDispositionAndKeepsOctetStream() throws Exception {
        var src = new InMemoryOrigin("forwarded bytes");

        Response response = service.stream(src);

        assertThat(response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(response.getMediaType()).hasToString(MediaType.APPLICATION_OCTET_STREAM);

        var out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        assertThat(out).hasToString("forwarded bytes");
    }

    @Test
    void attachmentEncodesUtf8Filename() {
        var src = new InMemoryOrigin("payload");

        Response response = service.attachment(src, "informe año.pdf");

        String disposition = response.getHeaderString(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .contains("filename=\"informe año.pdf\"")
                .contains("filename*=UTF-8''informe%20a%C3%B1o.pdf");
    }

    @Test
    void writeToCopiesBytesUsingConfiguredChunkSize() throws Exception {
        var src = new InMemoryOrigin("checksum me");
        service.chunkSize = 4;
        var out = new ByteArrayOutputStream();

        WriteResult result = service.writeTo(src, out);

        assertThat(out).hasToString("checksum me");
        assertThat(result.getBytesTransferred()).isEqualTo(11L);
    }

    @Test
    void attachmentPropagatesIOExceptionFromOrigin() {
        DownloadOrigin broken = InMemoryOrigin.failing("service-origin-down");
        Response response = service.attachment(broken, "x.bin");
        StreamingOutput body = (StreamingOutput) response.getEntity();

        assertThatThrownBy(() -> body.write(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessage("service-origin-down");
    }

    @Test
    void streamPropagatesIOExceptionFromOrigin() {
        DownloadOrigin broken = InMemoryOrigin.failing("service-stream-down");
        Response response = service.stream(broken);
        StreamingOutput body = (StreamingOutput) response.getEntity();

        assertThatThrownBy(() -> body.write(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
    }
}
