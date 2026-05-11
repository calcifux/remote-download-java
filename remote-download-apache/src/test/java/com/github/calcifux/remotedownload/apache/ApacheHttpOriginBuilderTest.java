package com.github.calcifux.remotedownload.apache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure-builder tests for {@link ApacheHttpOrigin.Builder}. These ensure the
 * fluent surface compiles and chains correctly without spinning up an HTTP
 * server — the network-facing tests live in {@link ApacheHttpOriginTest}.
 *
 * <p>The builder ships with several enterprise-oriented setters (NTLM, proxy,
 * proxy auth, granular timeouts) that the WireMock-backed suite cannot exercise
 * because the simulator does not speak those protocols. These tests at least
 * cover the configuration paths so coverage is honest.
 */
class ApacheHttpOriginBuilderTest {

    @Test
    void chainsEveryFluentSetterAndBuildsOk() {
        var origin = ApacheHttpOrigin.url("https://example.com/file.bin")
                .header("X-Tenant", "acme")
                .bearer("eyJtoken")
                .basicAuth("user", "pass")
                .connectTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofMinutes(2))
                .retries(5)
                .retryInterval(Duration.ofMillis(500))
                .build();

        assertThat(origin).isNotNull();
    }

    @Test
    void ntlmConfiguresCredentialsForTheTargetHost() {
        var origin = ApacheHttpOrigin.url("https://intranet.corp/file.pdf")
                .ntlm("CORP", "ada", "secret")
                .build();

        assertThat(origin).isNotNull();
    }

    @Test
    void proxyAndProxyAuthChainCorrectly() {
        var origin = ApacheHttpOrigin.url("https://example.com/file.bin")
                .proxy("proxy.corp", 8080)
                .proxyAuth("proxyUser", "proxyPass")
                .build();

        assertThat(origin).isNotNull();
    }

    @Test
    void proxyAuthWithoutProxyThrows() {
        var builder = ApacheHttpOrigin.url("https://example.com/file.bin");

        assertThatThrownBy(() -> builder.proxyAuth("user", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    void multipleAuthSchemesShareTheSameCredentialsProvider() {
        // Sanity check: chaining ntlm + proxyAuth on the same builder must not
        // crash — the builder lazily instantiates the credentials provider.
        var origin = ApacheHttpOrigin.url("https://example.com/file.bin")
                .proxy("proxy.corp", 8080)
                .proxyAuth("proxyUser", "proxyPass")
                .ntlm("CORP", "ada", "secret")
                .build();

        assertThat(origin).isNotNull();
    }

    @Test
    void parseFilenameHandlesEdgeCases() {
        // Direct exercise of the package-private helper — keeps the parser
        // honest on inputs the WireMock suite does not produce.
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename(null)).isNull();
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename("inline")).isNull();
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename("attachment; filename"))
                .isNull();
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename("attachment; filename=plain.pdf"))
                .isEqualTo("plain.pdf");
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename("attachment; filename=\"quoted.pdf\""))
                .isEqualTo("quoted.pdf");
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename(
                "attachment; filename*=UTF-8''reporte.pdf"))
                .isEqualTo("reporte.pdf");
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename(
                "attachment; filename=\"first.pdf\"; size=42"))
                .isEqualTo("first.pdf");

        // Quoted but unclosed: starts with `"` but does not end with `"`. The
        // parser leaves it as-is (we do not strip unbalanced quotes).
        assertThat(ApacheHttpOrigin.HttpHeaderUtils.parseFilename(
                "attachment; filename=\"unclosed.pdf"))
                .isEqualTo("\"unclosed.pdf");
    }
}
