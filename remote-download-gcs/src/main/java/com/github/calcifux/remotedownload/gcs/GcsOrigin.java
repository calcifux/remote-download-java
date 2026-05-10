package com.github.calcifux.remotedownload.gcs;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import com.github.calcifux.remotedownload.RemoteDownloadException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

/**
 * {@link DownloadOrigin} that reads objects from Google Cloud Storage.
 *
 * <p>Credentials are resolved in the following order:
 * <ol>
 *     <li>An explicit {@link GoogleCredentials} instance supplied through
 *         {@link Builder#credentials(GoogleCredentials)}</li>
 *     <li>A credentials file pointed to by
 *         {@link Builder#credentialsPath(String)}, accepting
 *         {@code classpath:}, {@code file:} and absolute filesystem paths</li>
 *     <li>Application Default Credentials: the
 *         {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable,
 *         {@code gcloud auth} configuration, or the metadata server
 *         (GKE / Cloud Run / Compute Engine)</li>
 * </ol>
 *
 * @since 1.0.0
 */
@Slf4j
public class GcsOrigin implements DownloadOrigin {

    private final String bucket;
    private final String objectName;
    private final String projectId;
    private final String credentialsPath;
    private final GoogleCredentials credentials;

    private GcsOrigin(Builder b) {
        this.bucket = b.bucket;
        this.objectName = b.objectName;
        this.projectId = b.projectId;
        this.credentialsPath = b.credentialsPath;
        this.credentials = b.credentials;
    }

    /**
     * @return a fluent builder for {@link GcsOrigin}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Opens the GCS object and exposes its contents as a live stream. The
     * underlying {@link ReadChannel} and {@link Storage} client are released
     * when the returned {@link RemoteContent} is closed.
     *
     * @return live remote content; the caller owns the returned object
     * @throws IOException             on GCS or network failures
     * @throws RemoteDownloadException   if the requested object does not exist
     */
    @Override
    public RemoteContent open() throws IOException {
        log.debug("[GcsOrigin] gs://{}/{}", bucket, objectName);

        StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
        if (projectId != null) {
            optionsBuilder.setProjectId(projectId);
        }

        GoogleCredentials creds = resolveCredentials();
        if (creds != null) {
            optionsBuilder.setCredentials(creds);
        }

        Storage storage = optionsBuilder.build().getService();
        Blob blob = storage.get(BlobId.of(bucket, objectName));
        if (blob == null) {
            throw new RemoteDownloadException(
                    "GcsOrigin: object not found at gs://" + bucket + "/" + objectName
            );
        }

        ReadChannel channel = blob.reader();
        InputStream stream = Channels.newInputStream(channel);

        return RemoteContent.builder()
                .inputStream(stream)
                .contentType(blob.getContentType())
                .contentLength(blob.getSize())
                .filename(extractFilename(objectName))
                .onClose(() -> {
                    try { channel.close(); } catch (Exception ignored) {}
                    try { storage.close(); } catch (Exception ignored) {}
                })
                .build();
    }

    /**
     * Resolves the credentials following the documented precedence order.
     *
     * @return resolved credentials, or {@code null} to fall back to ADC
     * @throws IOException if a credentials file cannot be read
     */
    private GoogleCredentials resolveCredentials() throws IOException {
        if (credentials != null) {
            return credentials;
        }
        if (credentialsPath != null) {
            try (InputStream is = openCredentialsInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(is);
            }
        }
        // Returning null lets StorageOptions fall back to Application Default Credentials.
        return null;
    }

    /**
     * Opens an input stream for the supplied credentials path. Accepts
     * {@code classpath:}, {@code file:} and absolute filesystem paths.
     */
    private InputStream openCredentialsInputStream(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String cp = path.substring("classpath:".length());
            InputStream is = getClass().getClassLoader().getResourceAsStream(cp);
            if (is == null) {
                throw new IOException("Classpath resource not found: " + cp);
            }
            return is;
        }
        if (path.startsWith("file:")) {
            return new FileInputStream(path.substring("file:".length()));
        }
        return new FileInputStream(path);
    }

    /**
     * Extracts the trailing path segment of the object name as the suggested
     * filename.
     */
    private static String extractFilename(String objectName) {
        int slash = objectName.lastIndexOf('/');
        return slash >= 0 ? objectName.substring(slash + 1) : objectName;
    }

    // ---------------------------------------------------------------------
    //  Fluent builder
    // ---------------------------------------------------------------------

    /**
     * Fluent builder for {@link GcsOrigin}.
     */
    public static class Builder {
        private String bucket;
        private String objectName;
        private String projectId;
        private String credentialsPath;
        private GoogleCredentials credentials;

        /** Sets the GCS bucket (required). */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /** Sets the object name within the bucket (required). */
        public Builder object(String objectName) {
            this.objectName = objectName;
            return this;
        }

        /**
         * Sets the GCP project id explicitly. When omitted the value is taken
         * from Application Default Credentials.
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets a service-account JSON file. Accepted prefixes:
         * <ul>
         *     <li>{@code classpath:path/in/jar.json}</li>
         *     <li>{@code file:/absolute/path.json}</li>
         *     <li>{@code /absolute/path.json}</li>
         * </ul>
         */
        public Builder credentialsPath(String path) {
            this.credentialsPath = path;
            return this;
        }

        /**
         * Supplies pre-built {@link GoogleCredentials}, useful when
         * credentials come from a vault or are derived programmatically.
         */
        public Builder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        /**
         * @return an immutable {@link GcsOrigin} configured with this builder
         * @throws IllegalStateException if {@code bucket} or {@code object} is missing
         */
        public GcsOrigin build() {
            if (bucket == null || objectName == null) {
                throw new IllegalStateException("GcsOrigin: bucket and object are required");
            }
            return new GcsOrigin(this);
        }
    }
}
