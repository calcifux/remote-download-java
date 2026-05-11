package com.github.calcifux.remotedownload;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteContentTest {

    @Test
    void optionalsAreEmptyWhenMetadataIsMissing() throws Exception {
        try (RemoteContent content = RemoteContent.builder()
                .inputStream(new ByteArrayInputStream(new byte[0]))
                .build()) {

            assertThat(content.contentType()).isEmpty();
            assertThat(content.contentLength()).isEmpty();
            assertThat(content.filename()).isEmpty();
        }
    }

    @Test
    void optionalsExposeValuesWhenMetadataIsPresent() throws Exception {
        try (RemoteContent content = RemoteContent.builder()
                .inputStream(new ByteArrayInputStream(new byte[0]))
                .contentType("application/pdf")
                .contentLength(123L)
                .filename("doc.pdf")
                .build()) {

            assertThat(content.contentType()).contains("application/pdf");
            assertThat(content.contentLength()).contains(123L);
            assertThat(content.filename()).contains("doc.pdf");
        }
    }

    @Test
    void closeRunsOnCloseHookAfterStreamIsClosed() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        var content = RemoteContent.builder()
                .inputStream(new ByteArrayInputStream(new byte[0]))
                .onClose(() -> closed.set(true))
                .build();

        content.close();

        assertThat(closed).isTrue();
    }

    @Test
    void closeSwallowsExceptionsFromOnCloseHook() throws Exception {
        // Contract: the primary stream close already succeeded; the hook is
        // best-effort cleanup, so its failure must not surface to the caller.
        var content = RemoteContent.builder()
                .inputStream(new ByteArrayInputStream(new byte[0]))
                .onClose(() -> { throw new RuntimeException("cleanup-failed"); })
                .build();

        // Should not throw.
        content.close();
    }

    @Test
    void closePropagatesIOExceptionFromInputStream() {
        InputStream throwingStream = new InputStream() {
            @Override public int read() { return -1; }
            @Override public void close() throws IOException { throw new IOException("stream-broken"); }
        };

        var content = RemoteContent.builder()
                .inputStream(throwingStream)
                .build();

        assertThatThrownBy(content::close)
                .isInstanceOf(IOException.class)
                .hasMessage("stream-broken");
    }

    @Test
    void closeWithNullInputStreamStillRunsOnCloseHook() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        var content = RemoteContent.builder()
                .onClose(() -> closed.set(true))
                .build();

        content.close();

        assertThat(closed).isTrue();
    }
}
