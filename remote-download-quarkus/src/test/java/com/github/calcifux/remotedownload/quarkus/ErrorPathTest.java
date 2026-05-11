package com.github.calcifux.remotedownload.quarkus;

import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.quarkus.support.InMemoryOrigin;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the error-handling branches of the {@link Downloads} static factory —
 * the {@code catch (IOException)} that logs the failure and re-throws so the
 * JAX-RS runtime can map it to a 5xx response.
 *
 * <p>The CDI service equivalent lives in
 * {@code core/RemoteDownloadJaxRsServiceTest} because it needs access to the
 * package-private {@code chunkSize} / {@code defaultDisposition} fields.
 */
class ErrorPathTest {

    @Test
    void downloadsAttachmentPropagatesIOExceptionFromOrigin() {
        DownloadOrigin broken = InMemoryOrigin.failing("origin-down");
        Response response = Downloads.attachment(broken, "x.bin");
        StreamingOutput body = (StreamingOutput) response.getEntity();

        assertThatThrownBy(() -> body.write(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessage("origin-down");
    }

    @Test
    void downloadsStreamPropagatesIOExceptionFromOrigin() {
        DownloadOrigin broken = InMemoryOrigin.failing("origin-down");
        Response response = Downloads.stream(broken);
        StreamingOutput body = (StreamingOutput) response.getEntity();

        assertThatThrownBy(() -> body.write(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void downloadsInlinePropagatesIOExceptionFromOrigin() {
        DownloadOrigin broken = InMemoryOrigin.failing("inline-down");
        Response response = Downloads.inline(broken, "x.bin");
        StreamingOutput body = (StreamingOutput) response.getEntity();

        assertThatThrownBy(() -> body.write(new ByteArrayOutputStream()))
                .isInstanceOf(IOException.class)
                .hasMessage("inline-down");
    }
}
