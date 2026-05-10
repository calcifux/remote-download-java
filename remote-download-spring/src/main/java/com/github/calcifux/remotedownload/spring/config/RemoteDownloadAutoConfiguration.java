package com.github.calcifux.remotedownload.spring.config;

import com.github.calcifux.remotedownload.spring.core.RemoteDownloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for {@code remote-download}.
 *
 * <p>Registers a {@link RemoteDownloadService} singleton driven by
 * {@link RemoteDownloadProperties}. The auto-configuration is gated by the
 * {@code remote-download.enabled} property; absence of the property is treated
 * as enabled to keep zero-configuration behaviour.
 *
 * <p>Applications can override the registered bean by exposing their own
 * {@link RemoteDownloadService} — the {@link ConditionalOnMissingBean} guard
 * prevents collisions.
 *
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RemoteDownloadProperties.class)
@ConditionalOnProperty(prefix = "remote-download", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RemoteDownloadAutoConfiguration {

    /**
     * Builds and registers the default {@link RemoteDownloadService}.
     *
     * @param properties bound configuration
     * @return service ready to be injected by the application
     */
    @Bean
    @ConditionalOnMissingBean(RemoteDownloadService.class)
    public RemoteDownloadService streamRemoteService(RemoteDownloadProperties properties) {
        log.info("[RemoteDownloadAutoConfiguration] Registering RemoteDownloadService chunkSize={} disposition={}",
                properties.getChunkSize(), properties.getDefaultDisposition());
        return new RemoteDownloadService(properties);
    }
}
