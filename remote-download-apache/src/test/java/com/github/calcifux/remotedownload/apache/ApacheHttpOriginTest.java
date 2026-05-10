package com.github.calcifux.remotedownload.apache;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.RemoteDownload;
import com.github.calcifux.remotedownload.RemoteDownloadException;
import com.github.calcifux.remotedownload.WriteResult;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class ApacheHttpOriginTest {

    @Test
    void streamsBodyOk(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/file.pdf").willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/pdf")
                .withBody("PDF bytes")));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/file.pdf").build();
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

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/file.pdf").build();

        try (RemoteContent content = RemoteDownload.from(src).fetch()) {
            assertThat(content.contentType()).contains("application/pdf");
            assertThat(content.contentLength()).contains(9L);
            assertThat(content.filename()).contains("report.pdf");
        }
    }

    @Test
    void throwsOnHttp404(WireMockRuntimeInfo info) {
        stubFor(get("/missing").willReturn(aResponse().withStatus(404)));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/missing").build();

        assertThatThrownBy(() -> RemoteDownload.from(src).writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(RemoteDownloadException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void sendsBearerHeader(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/private").willReturn(aResponse().withStatus(200).withBody("ok")));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/private")
                .bearer("eyJhbGc.token")
                .build();

        RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());

        verify(getRequestedFor(urlEqualTo("/private"))
                .withHeader("Authorization", equalTo("Bearer eyJhbGc.token")));
    }

    @Test
    void sendsBasicAuthHeader(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/auth").willReturn(aResponse().withStatus(200).withBody("ok")));

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/auth")
                .basicAuth("user", "pass")
                .build();

        RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());

        verify(getRequestedFor(urlEqualTo("/auth"))
                .withHeader("Authorization", equalTo(expected)));
    }

    @Test
    void sendsCustomHeaders(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/h").willReturn(aResponse().withStatus(200).withBody("ok")));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/h")
                .header("X-Tenant", "acme")
                .header("Accept-Language", "en-US")
                .build();

        RemoteDownload.from(src).writeTo(new ByteArrayOutputStream());

        verify(getRequestedFor(urlEqualTo("/h"))
                .withHeader("X-Tenant", equalTo("acme"))
                .withHeader("Accept-Language", equalTo("en-US")));
    }

    @Test
    void retriesOn5xxAndEventuallySucceeds(WireMockRuntimeInfo info) throws Exception {
        // Two failures then success — Apache HttpClient retry kicks in
        stubFor(get("/flaky")
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("attempt-2"));

        stubFor(get("/flaky")
                .inScenario("flaky")
                .whenScenarioStateIs("attempt-2")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("attempt-3"));

        stubFor(get("/flaky")
                .inScenario("flaky")
                .whenScenarioStateIs("attempt-3")
                .willReturn(aResponse().withStatus(200).withBody("recovered")));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/flaky")
                .retries(3)
                .retryInterval(Duration.ofMillis(10))
                .build();

        var out = new ByteArrayOutputStream();
        WriteResult result = RemoteDownload.from(src).writeTo(out);

        assertThat(out.toString()).isEqualTo("recovered");
        assertThat(result.getBytesTransferred()).isEqualTo(9L);
        // Two failures + one success = 3 total requests
        verify(moreThanOrExactly(3), getRequestedFor(urlEqualTo("/flaky")));
    }

    @Test
    void retriesExhaustedThrows(WireMockRuntimeInfo info) {
        stubFor(get("/always-down").willReturn(aResponse().withStatus(503)));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/always-down")
                .retries(2)
                .retryInterval(Duration.ofMillis(10))
                .build();

        assertThatThrownBy(() -> RemoteDownload.from(src).writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(RemoteDownloadException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void writeResultIncludesChecksumWhenRequested(WireMockRuntimeInfo info) throws Exception {
        stubFor(get("/file").willReturn(aResponse().withStatus(200).withBody("hello")));

        var src = ApacheHttpOrigin.url(info.getHttpBaseUrl() + "/file").build();
        WriteResult result = RemoteDownload.from(src)
                .checksum("SHA-256")
                .writeTo(new ByteArrayOutputStream());

        assertThat(result.checksum())
                .contains("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
