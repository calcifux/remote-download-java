package com.github.calcifux.remotedownload.spring.config;

import com.github.calcifux.remotedownload.RemoteDownload;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the Spring Boot starter, bound from
 * properties prefixed with {@code remote-download}.
 *
 * <p>Example {@code application.yml}:
 *
 * <pre>{@code
 * remote-download:
 *   enabled: true
 *   chunk-size: 8192
 *   default-disposition: attachment
 * }</pre>
 *
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remote-download")
public class RemoteDownloadProperties {

    /**
     * Toggles the auto-configuration. Defaults to {@code true}.
     */
    private boolean enabled = true;

    /**
     * Buffer size, in bytes, used when copying bytes to the client. Defaults
     * to {@link RemoteDownload#DEFAULT_CHUNK_SIZE} (8 KiB). Higher values lower
     * the syscall rate at the cost of memory pressure per concurrent transfer.
     */
    private int chunkSize = RemoteDownload.DEFAULT_CHUNK_SIZE;

    /**
     * Default {@code Content-Disposition} value used when none is supplied
     * explicitly. Common values are {@code "attachment"} (forces download)
     * and {@code "inline"} (renders in the browser when possible).
     */
    private String defaultDisposition = "attachment";
}
