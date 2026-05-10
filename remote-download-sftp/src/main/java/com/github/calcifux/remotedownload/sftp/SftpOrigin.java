package com.github.calcifux.remotedownload.sftp;

import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.RemoteDownloadException;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * {@link DownloadOrigin} that reads files from a remote SFTP server using
 * Apache Mina SSHD as the underlying client.
 *
 * <p>Supports the two most common authentication modes:
 * <ul>
 *     <li>Password authentication — pass the secret to {@link Builder#password(String)}</li>
 *     <li>Public-key authentication — point {@link Builder#privateKey(String)} at a
 *         PEM file (RSA, ECDSA, Ed25519, ...). The file must be readable by the
 *         JVM process and is loaded through {@link FileKeyPairProvider}.</li>
 * </ul>
 *
 * <p>Construct instances through the {@link #builder()} fluent builder:
 *
 * <pre>{@code
 * DownloadOrigin source = SftpOrigin.builder()
 *         .host("sftp.example.com")
 *         .port(22)
 *         .user("svc-downloads")
 *         .privateKey("/etc/keys/sftp_id_rsa")
 *         .path("/uploads/contracts/abc.pdf")
 *         .build();
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
public class SftpOrigin implements DownloadOrigin {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String privateKeyPath;
    private final String path;
    private final Duration connectTimeout;
    private final Duration authTimeout;

    private SftpOrigin(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.user = b.user;
        this.password = b.password;
        this.privateKeyPath = b.privateKeyPath;
        this.path = b.path;
        this.connectTimeout = b.connectTimeout;
        this.authTimeout = b.authTimeout;
    }

    /**
     * @return a fluent builder for {@link SftpOrigin}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Opens an SFTP session, authenticates, opens the remote file and exposes
     * its contents as a live {@link InputStream}. The SSH client, session and
     * SFTP channel are released when the returned {@link RemoteContent} is
     * closed.
     *
     * @return live remote content; the caller owns the returned object
     * @throws IOException on network, authentication or file-not-found errors
     */
    @Override
    public RemoteContent open() throws IOException {
        log.debug("[SftpOrigin] sftp://{}@{}:{}{}", user, host, port, path);

        SshClient client = SshClient.setUpDefaultClient();
        client.start();

        ClientSession session;
        SftpClient sftp;
        InputStream stream;
        try {
            session = client.connect(user, host, port)
                    .verify(connectTimeout.toMillis())
                    .getSession();

            if (password != null) {
                session.addPasswordIdentity(password);
            }
            if (privateKeyPath != null) {
                FileKeyPairProvider keyProvider =
                        new FileKeyPairProvider(Paths.get(privateKeyPath));
                session.setKeyIdentityProvider(keyProvider);
            }
            if (password == null && privateKeyPath == null) {
                throw new IllegalStateException(
                        "SftpOrigin: either password or privateKey must be supplied"
                );
            }

            session.auth().verify(authTimeout.toMillis());

            sftp = SftpClientFactory.instance().createSftpClient(session);
            stream = sftp.read(path);
        } catch (Exception e) {
            try { client.stop(); } catch (Exception ignored) {}
            throw new RemoteDownloadException(
                    "SFTP error opening sftp://" + host + ":" + port + path, e);
        }

        // Capture references for the cleanup closure.
        final SftpClient sftpRef = sftp;
        final ClientSession sessionRef = session;

        Runnable cleanup = () -> {
            try { sftpRef.close(); } catch (Exception ignored) {}
            try { sessionRef.close(); } catch (Exception ignored) {}
            try { client.stop(); } catch (Exception ignored) {}
        };

        return RemoteContent.builder()
                .inputStream(stream)
                .filename(extractFilename(path))
                .onClose(cleanup)
                .build();
    }

    /**
     * Extracts the trailing path segment as the suggested filename.
     */
    private static String extractFilename(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // ---------------------------------------------------------------------
    //  Fluent builder
    // ---------------------------------------------------------------------

    /**
     * Fluent builder for {@link SftpOrigin}.
     */
    public static class Builder {
        private String host;
        private int port = 22;
        private String user;
        private String password;
        private String privateKeyPath;
        private String path;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration authTimeout = Duration.ofSeconds(30);

        /** Sets the target SFTP host (required). */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /** Sets the SFTP port. Defaults to 22. */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /** Sets the SFTP user (required). */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /** Configures password authentication. Mutually exclusive with key auth. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Configures public-key authentication using a PEM file from the local
         * filesystem.
         */
        public Builder privateKey(String filesystemPath) {
            this.privateKeyPath = filesystemPath;
            return this;
        }

        /** Sets the remote path of the file to download (required). */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /** Sets the TCP connect timeout. Defaults to 30 seconds. */
        public Builder connectTimeout(Duration d) {
            this.connectTimeout = d;
            return this;
        }

        /** Sets the SSH authentication timeout. Defaults to 30 seconds. */
        public Builder authTimeout(Duration d) {
            this.authTimeout = d;
            return this;
        }

        /**
         * @return an immutable {@link SftpOrigin} configured with this builder
         * @throws IllegalStateException if any of {@code host}, {@code user} or
         *                               {@code path} are missing
         */
        public SftpOrigin build() {
            if (host == null || user == null || path == null) {
                throw new IllegalStateException(
                        "SftpOrigin: host, user and path are required"
                );
            }
            return new SftpOrigin(this);
        }
    }
}
