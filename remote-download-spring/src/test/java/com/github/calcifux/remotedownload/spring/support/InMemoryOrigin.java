package com.github.calcifux.remotedownload.spring.support;

import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteContent;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Tiny in-memory {@link DownloadOrigin} used by the Spring module test suite.
 *
 * <p>Lets tests exercise the response wiring without spinning up an HTTP server.
 */
public final class InMemoryOrigin implements DownloadOrigin {

    private final byte[] payload;
    private final String contentType;
    private final String filename;

    public InMemoryOrigin(String payload) {
        this(payload.getBytes(StandardCharsets.UTF_8), "application/octet-stream", null);
    }

    public InMemoryOrigin(byte[] payload, String contentType, String filename) {
        this.payload = payload;
        this.contentType = contentType;
        this.filename = filename;
    }

    @Override
    public RemoteContent open() {
        return RemoteContent.builder()
                .inputStream(new ByteArrayInputStream(payload))
                .contentType(contentType)
                .contentLength((long) payload.length)
                .filename(filename)
                .build();
    }

    /**
     * Builds an origin whose {@link DownloadOrigin#open()} throws — useful to
     * exercise the error-handling branches of the response wiring.
     */
    public static DownloadOrigin failing(String message) {
        return () -> { throw new java.io.IOException(message); };
    }
}
