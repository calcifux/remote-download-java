package com.github.calcifux.remotedownload.spring.config;

import com.github.calcifux.remotedownload.spring.core.RemoteDownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteDownloadAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RemoteDownloadAutoConfiguration.class));

    @Test
    void registersServiceAndPropertiesByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RemoteDownloadService.class);
            assertThat(context).hasSingleBean(RemoteDownloadProperties.class);
            RemoteDownloadProperties props = context.getBean(RemoteDownloadProperties.class);
            assertThat(props.getChunkSize()).isEqualTo(8 * 1024);
            assertThat(props.getDefaultDisposition()).isEqualTo("attachment");
        });
    }

    @Test
    void bindsPropertiesFromConfiguration() {
        runner.withPropertyValues(
                "remote-download.chunk-size=4096",
                "remote-download.default-disposition=inline"
        ).run(context -> {
            RemoteDownloadProperties props = context.getBean(RemoteDownloadProperties.class);
            assertThat(props.getChunkSize()).isEqualTo(4096);
            assertThat(props.getDefaultDisposition()).isEqualTo("inline");
        });
    }

    @Test
    void disabledWhenEnabledIsFalse() {
        runner.withPropertyValues("remote-download.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RemoteDownloadService.class);
        });
    }

    @Test
    void userDefinedServiceOverridesAutoConfiguredOne() {
        runner.withUserConfiguration(CustomServiceConfig.class).run(context -> {
            assertThat(context).hasSingleBean(RemoteDownloadService.class);
            assertThat(context.getBean(RemoteDownloadService.class))
                    .isSameAs(context.getBean("customService"));
        });
    }

    @Configuration
    static class CustomServiceConfig {
        @Bean
        RemoteDownloadService customService() {
            return new RemoteDownloadService(new RemoteDownloadProperties());
        }
    }
}
