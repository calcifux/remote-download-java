package com.github.calcifux.remotedownload.spring;

import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.spring.config.RemoteDownloadProperties;
import com.github.calcifux.remotedownload.spring.core.RemoteDownloadService;
import com.github.calcifux.remotedownload.spring.support.InMemoryOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the error-handling branches of {@link Downloads} and
 * {@link RemoteDownloadService} — the {@code catch (IOException)} that logs
 * the failure and re-throws so Spring MVC can map it to a 5xx response.
 */
class ErrorPathTest {

    @Test
    void downloadsAttachmentPropagatesIOExceptionFromOrigin() {
        DownloadOrigin broken = InMemoryOrigin.failing("origin-down");
        StreamingResponseBody body = Downloads.attachment(broken, "x.bin").getBody();

        assertThatThrownBy(() -> body.writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessage("origin-down");
    }

    @Test
    void downloadsStreamPropagatesIOExceptionFromOrigin() {
        DownloadOrigin broken = InMemoryOrigin.failing("origin-down");
        StreamingResponseBody body = Downloads.stream(broken).getBody();

        assertThatThrownBy(() -> body.writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void servicePropagatesIOExceptionFromOrigin() {
        var service = new RemoteDownloadService(new RemoteDownloadProperties());
        DownloadOrigin broken = InMemoryOrigin.failing("service-origin-down");
        StreamingResponseBody body = service.attachment(broken, "x.bin").getBody();

        assertThatThrownBy(() -> body.writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessage("service-origin-down");
    }
}
