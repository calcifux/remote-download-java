package com.github.calcifux.remotedownload;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteDownloadExceptionTest {

    @Test
    void messageOnlyConstructor() {
        var ex = new RemoteDownloadException("boom");

        assertThat(ex).hasMessage("boom").hasNoCause();
    }

    @Test
    void messageAndCauseConstructor() {
        var cause = new IOException("network down");
        var ex = new RemoteDownloadException("wrapper", cause);

        assertThat(ex).hasMessage("wrapper").hasCause(cause);
    }

    @Test
    void causeOnlyConstructorDelegatesMessageToCause() {
        var cause = new IOException("network down");
        var ex = new RemoteDownloadException(cause);

        assertThat(ex).hasCause(cause);
        // RuntimeException(Throwable) sets the message to cause.toString()
        assertThat(ex.getMessage()).contains("network down");
    }

    @Test
    void isUncheckedSoCanBeThrownWithoutDeclaring() {
        assertThatThrownBy(() -> {
            throw new RemoteDownloadException("propagated");
        })
                .isInstanceOf(RuntimeException.class)
                .isInstanceOf(RemoteDownloadException.class)
                .hasMessage("propagated");
    }
}
