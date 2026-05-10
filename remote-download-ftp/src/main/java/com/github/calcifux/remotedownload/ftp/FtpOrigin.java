package com.github.calcifux.remotedownload.ftp;

import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.RemoteDownloadException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * {@link DownloadOrigin} that reads files from a remote FTP server using
 * Apache Commons Net as the underlying client. When {@link Builder#secure(boolean)}
 * is enabled the connection negotiates explicit FTPS via {@link FTPSClient}.
 *
 * <p>Operates in passive mode by default — the recommended setting when the
 * client sits behind a NAT or firewall, which covers most cloud workloads.
 *
 * <p>Construct instances through the {@link #builder()} fluent builder:
 *
 * <pre>{@code
 * DownloadOrigin source = FtpOrigin.builder()
 *         .host("ftp.example.com")
 *         .user("anonymous")
 *         .password("guest@example.com")
 *         .path("/pub/file.zip")
 *         .build();
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
public class FtpOrigin implements DownloadOrigin {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String path;
    private final boolean secure;
    private final boolean passive;
    private final Duration connectTimeout;
    private final Duration dataTimeout;

    private FtpOrigin(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.user = b.user;
        this.password = b.password;
        this.path = b.path;
        this.secure = b.secure;
        this.passive = b.passive;
        this.connectTimeout = b.connectTimeout;
        this.dataTimeout = b.dataTimeout;
    }

    /**
     * @return a fluent builder for {@link FtpOrigin}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Connects to the FTP / FTPS server, authenticates, opens the remote file
     * in binary mode and exposes its contents as a live {@link InputStream}.
     * The control channel is kept alive until the returned
     * {@link RemoteContent} is closed.
     *
     * @return live remote content; the caller owns the returned object
     * @throws IOException on network, authentication or file-not-found errors
     */
    @Override
    public RemoteContent open() throws IOException {
        log.debug("[FtpOrigin] {}://{}@{}:{}{}",
                secure ? "ftps" : "ftp", user, host, port, path);

        FTPClient ftp = secure ? new FTPSClient() : new FTPClient();
        ftp.setConnectTimeout((int) connectTimeout.toMillis());

        try {
            ftp.connect(host, port);
            ftp.setDataTimeout(dataTimeout);

            if (!ftp.login(user, password)) {
                String reply = ftp.getReplyString();
                ftp.disconnect();
                throw new RemoteDownloadException("FTP login failed: " + reply);
            }

            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            if (passive) {
                ftp.enterLocalPassiveMode();
            }

            InputStream stream = ftp.retrieveFileStream(path);
            if (stream == null) {
                String reply = ftp.getReplyString();
                ftp.logout();
                ftp.disconnect();
                throw new RemoteDownloadException(
                        "FTP cannot open " + path + ": " + reply);
            }

            Runnable cleanup = () -> {
                try { ftp.completePendingCommand(); } catch (IOException ignored) {}
                try { ftp.logout(); } catch (IOException ignored) {}
                try { ftp.disconnect(); } catch (IOException ignored) {}
            };

            return RemoteContent.builder()
                    .inputStream(stream)
                    .filename(extractFilename(path))
                    .onClose(cleanup)
                    .build();

        } catch (IOException e) {
            try { ftp.disconnect(); } catch (IOException ignored) {}
            throw e;
        }
    }

    /** Extracts the trailing path segment as the suggested filename. */
    private static String extractFilename(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // ---------------------------------------------------------------------
    //  Fluent builder
    // ---------------------------------------------------------------------

    /**
     * Fluent builder for {@link FtpOrigin}.
     */
    public static class Builder {
        private String host;
        private int port = 21;
        private String user = "anonymous";
        private String password = "anonymous@";
        private String path;
        private boolean secure = false;
        private boolean passive = true;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration dataTimeout = Duration.ofMinutes(5);

        /** Sets the target FTP host (required). */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /** Sets the FTP port. Defaults to 21. */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /** Sets the FTP user. Defaults to {@code anonymous}. */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /** Sets the FTP password. Defaults to {@code anonymous@}. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** Sets the remote path of the file to download (required). */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Enables explicit FTPS (TLS) on the control channel. Defaults to
         * {@code false} (plain FTP).
         */
        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Toggles passive mode (PASV). Defaults to {@code true}; disable only
         * when the server explicitly requires active mode.
         */
        public Builder passive(boolean passive) {
            this.passive = passive;
            return this;
        }

        /** Sets the TCP connect timeout. Defaults to 30 seconds. */
        public Builder connectTimeout(Duration d) {
            this.connectTimeout = d;
            return this;
        }

        /** Sets the data-channel inactivity timeout. Defaults to 5 minutes. */
        public Builder dataTimeout(Duration d) {
            this.dataTimeout = d;
            return this;
        }

        /**
         * @return an immutable {@link FtpOrigin} configured with this builder
         * @throws IllegalStateException if {@code host} or {@code path} are missing
         */
        public FtpOrigin build() {
            if (host == null || path == null) {
                throw new IllegalStateException("FtpOrigin: host and path are required");
            }
            return new FtpOrigin(this);
        }
    }
}
