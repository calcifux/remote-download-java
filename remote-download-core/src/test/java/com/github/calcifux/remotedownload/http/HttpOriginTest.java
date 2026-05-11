package com.github.calcifux.remotedownload.http;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.RemoteDownload;
import com.github.calcifux.remotedownload.RemoteDownloadException;
import com.github.calcifux.remotedownload.WriteResult;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class HttpOriginTest {

    @Test
    void streamsBodyOk(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/file.pdf").willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/pdf")
                .withBody("PDF bytes")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/file.pdf").build();
        var out = new ByteArrayOutputStream();

        WriteResult result = RemoteDownload.from(src).writeTo(out);

        assertThat(out.toString()).isEqualTo("PDF bytes");
        assertThat(result.getBytesTransferred()).isEqualTo(9L);
    }

    @Test
    void exposesContentMetadata(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/file.pdf").willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/pdf")
                .withHeader("Content-Length", "9")
                .withHeader("Content-Disposition", "attachment; filename=\"report.pdf\"")
                .withBody("PDF bytes")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/file.pdf").build();

        try (RemoteContent content = RemoteDownload.from(src).fetch()) {
            assertThat(content.contentType()).contains("application/pdf");
            assertThat(content.contentLength()).contains(9L);
            assertThat(content.filename()).contains("report.pdf");
        }
    }

    @Test
    void parsesUtf8Filename(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/file").willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Disposition",
                        "attachment; filename=\"plain.pdf\"; filename*=UTF-8''reporte.pdf")
                .withBody("ok")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/file").build();
        try (RemoteContent content = RemoteDownload.from(src).fetch()) {
            assertThat(content.filename()).isPresent();
        }
    }

    @Test
    void throwsOnHttp404(WireMockRuntimeInfo info) {
        stubFor(get("/missing").willReturn(aResponse().withStatus(404)));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/missing").build();

        assertThatThrownBy(() -> RemoteDownload.from(src).writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(RemoteDownloadException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void throwsOnHttp500(WireMockRuntimeInfo info) {
        stubFor(get("/boom").willReturn(aResponse().withStatus(500)));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/boom").build();

        assertThatThrownBy(() -> RemoteDownload.from(src).writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(RemoteDownloadException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void sendsBearerHeader(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/private").willReturn(aResponse().withStatus(200).withBody("ok")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/private")
                .bearer("eyJhbGc.token")
                .build();

        RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());

        verify(getRequestedFor(urlEqualTo("/private"))
                .withHeader("Authorization", equalTo("Bearer eyJhbGc.token")));
    }

    @Test
    void sendsBasicAuth(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/auth").willReturn(aResponse().withStatus(200).withBody("ok")));

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/auth")
                .basicAuth("user", "pass")
                .build();

        RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());

        verify(getRequestedFor(urlEqualTo("/auth"))
                .withHeader("Authorization", equalTo(expected)));
    }

    @Test
    void sendsCustomHeader(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/h").willReturn(aResponse().withStatus(200).withBody("ok")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/h")
                .header("X-Tenant", "acme")
                .header("Accept-Language", "en-US")
                .build();

        RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());

        verify(getRequestedFor(urlEqualTo("/h"))
                .withHeader("X-Tenant", equalTo("acme"))
                .withHeader("Accept-Language", equalTo("en-US")));
    }

    @Test
    void writeResultIncludesChecksumWhenRequested(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/file").willReturn(aResponse().withStatus(200).withBody("hello")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/file").build();
        WriteResult result = RemoteDownload.from(src)
                .checksum("SHA-256")
                .writeTo(new ByteArrayOutputStream());

        assertThat(result.checksum())
                .contains("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void parseFilenameReturnsNullWhenHeaderHasNoFilenameParam() {
        // Direct exercise of the package-private parser — keeps the regex
        // false-path honest. The WireMock-backed tests cannot send a
        // Content-Disposition without a filename param (they always include
        // one), so cover it here.
        assertThat(HttpOrigin.parseFilename("attachment")).isNull();
        assertThat(HttpOrigin.parseFilename("inline; size=42")).isNull();
        assertThat(HttpOrigin.parseFilename(null)).isNull();
    }

    @Test
    void exposesEmptyOptionalsWhenResponseLacksMetadataHeaders(WireMockRuntimeInfo info) throws Exception {
        // No Content-Type, no Content-Length, no Content-Disposition.
        stubFor(get("/bare").willReturn(aResponse().withStatus(200).withBody("ok")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/bare").build();
        try (RemoteContent content = RemoteDownload.from(src).fetch()) {
            assertThat(content.contentType()).isEmpty();
            assertThat(content.contentLength()).isEmpty();
            assertThat(content.filename()).isEmpty();
        }
    }

    @Test
    void builderSettersAreChainable(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/file").willReturn(aResponse().withStatus(200).withBody("ok")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/file")
                .connectTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(15))
                .header("X-Custom", "v")
                .build();

        WriteResult result = RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());

        assertThat(result.getBytesTransferred()).isEqualTo(2L);
    }

    @Test
    void interruptedThreadDuringSendThrowsRemoteDownloadException(WireMockRuntimeInfo info) throws Exception {
        // Stub a response that takes a minute — we'll interrupt before then.
        stubFor(get("/hang").willReturn(aResponse()
                .withFixedDelay(60_000)
                .withStatus(200)
                .withBody("never")));

        var src = HttpOrigin.url(info.getHttpBaseUrl() + "/hang")
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        worker.start();
        Thread.sleep(300);   // give it time to enter client.send()
        worker.interrupt();
        worker.join(5_000);

        assertThat(caught.get())
                .isInstanceOf(RemoteDownloadException.class)
                .hasMessageContaining("Interrupted");
    }
}
