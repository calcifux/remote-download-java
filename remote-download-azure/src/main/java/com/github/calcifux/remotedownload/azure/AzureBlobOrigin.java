package com.github.calcifux.remotedownload.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * {@link DownloadOrigin} that reads blobs from Azure Blob Storage.
 *
 * <p>Supports the two most common authentication modes:
 * <ul>
 *     <li>Connection string — the simplest path; the secret carries both the
 *         endpoint and the credentials</li>
 *     <li>Endpoint + SAS token — recommended when sharing time-bounded access</li>
 * </ul>
 *
 * <p>Construct instances through the {@link #builder()} fluent builder:
 *
 * <pre>{@code
 * DownloadOrigin source = AzureBlobOrigin.builder()
 *         .container("my-container")
 *         .blob("contracts/2026/abc.pdf")
 *         .connectionString(System.getenv("AZURE_STORAGE_CONNECTION_STRING"))
 *         .build();
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
public class AzureBlobOrigin implements DownloadOrigin {

    private final String containerName;
    private final String blobName;
    private final String connectionString;
    private final String endpoint;
    private final String sasToken;

    private AzureBlobOrigin(Builder b) {
        this.containerName = b.containerName;
        this.blobName = b.blobName;
        this.connectionString = b.connectionString;
        this.endpoint = b.endpoint;
        this.sasToken = b.sasToken;
    }

    /**
     * @return a fluent builder for {@link AzureBlobOrigin}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Opens the blob and returns its contents as a live stream.
     *
     * @return live remote content; the caller owns the returned object
     * @throws IOException on Azure SDK or network failures
     */
    @Override
    public RemoteContent open() throws IOException {
        log.debug("[AzureBlobOrigin] {}/{}", containerName, blobName);

        BlobClientBuilder cb = new BlobClientBuilder()
                .containerName(containerName)
                .blobName(blobName);

        if (connectionString != null) {
            cb.connectionString(connectionString);
        } else if (endpoint != null) {
            cb.endpoint(endpoint);
            if (sasToken != null) {
                cb.sasToken(sasToken);
            }
        } else {
            throw new IllegalStateException(
                    "AzureBlobOrigin: either connectionString or endpoint must be provided"
            );
        }

        BlobClient client = cb.buildClient();
        BlobProperties props = client.getProperties();
        BlobInputStream stream = client.openInputStream();

        return RemoteContent.builder()
                .inputStream(stream)
                .contentType(props.getContentType())
                .contentLength(props.getBlobSize())
                .filename(extractFilename(blobName))
                .build();
    }

    /**
     * Extracts the trailing path segment of the blob name as the suggested
     * filename.
     */
    private static String extractFilename(String blob) {
        int slash = blob.lastIndexOf('/');
        return slash >= 0 ? blob.substring(slash + 1) : blob;
    }

    // ---------------------------------------------------------------------
    //  Fluent builder
    // ---------------------------------------------------------------------

    /**
     * Fluent builder for {@link AzureBlobOrigin}.
     */
    public static class Builder {
        private String containerName;
        private String blobName;
        private String connectionString;
        private String endpoint;
        private String sasToken;

        /** Sets the target container (required). */
        public Builder container(String containerName) {
            this.containerName = containerName;
            return this;
        }

        /** Sets the target blob name (required). */
        public Builder blob(String blobName) {
            this.blobName = blobName;
            return this;
        }

        /**
         * Sets the full Azure Storage connection string. When supplied this
         * takes precedence over {@link #endpoint(String)} / {@link #sasToken(String)}.
         */
        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        /**
         * Sets the storage endpoint URL (for example
         * {@code https://<account>.blob.core.windows.net}). Pair with
         * {@link #sasToken(String)} for SAS-based access.
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the SAS token used together with {@link #endpoint(String)}.
         */
        public Builder sasToken(String sasToken) {
            this.sasToken = sasToken;
            return this;
        }

        /**
         * @return an immutable {@link AzureBlobOrigin} configured with this builder
         * @throws IllegalStateException if {@code container} or {@code blob} is missing
         */
        public AzureBlobOrigin build() {
            if (containerName == null || blobName == null) {
                throw new IllegalStateException(
                        "AzureBlobOrigin: container and blob are required"
                );
            }
            return new AzureBlobOrigin(this);
        }
    }
}
