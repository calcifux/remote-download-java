package com.github.calcifux.remotedownload.s3;

import com.github.calcifux.remotedownload.RemoteContent;
import com.github.calcifux.remotedownload.DownloadOrigin;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.net.URI;

/**
 * {@link DownloadOrigin} that reads objects from Amazon S3 using AWS SDK v2.
 *
 * <p>Credentials are resolved in the following order:
 * <ol>
 *     <li>Static credentials supplied through
 *         {@link Builder#credentials(String, String)} or
 *         {@link Builder#credentials(AwsCredentialsProvider)}</li>
 *     <li>The default AWS credentials chain when no explicit provider is given:
 *         environment variables, {@code ~/.aws/credentials}, IAM roles
 *         (EC2 / ECS / EKS / Lambda)</li>
 * </ol>
 *
 * <p>The built-in region defaults to {@code us-east-1}; override it with
 * {@link Builder#region(String)} or {@link Builder#region(Region)} when targeting
 * another region. Use {@link Builder#endpoint(String)} for S3-compatible
 * services such as MinIO or LocalStack.
 *
 * @since 1.0.0
 */
@Slf4j
public class S3Origin implements DownloadOrigin {

    private final String bucket;
    private final String key;
    private final Region region;
    private final URI endpointOverride;
    private final AwsCredentialsProvider credentials;

    private S3Origin(Builder b) {
        this.bucket = b.bucket;
        this.key = b.key;
        this.region = b.region;
        this.endpointOverride = b.endpointOverride;
        this.credentials = b.credentials != null
                ? b.credentials
                : DefaultCredentialsProvider.create();
    }

    /**
     * @return a fluent builder for {@link S3Origin}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Issues a {@code GetObject} request and exposes the response body as
     * a live stream. The created {@link S3Client} is released when the
     * returned {@link RemoteContent} is closed.
     *
     * @return live remote content; the caller owns the returned object
     * @throws IOException on AWS SDK or network failures
     */
    @Override
    public RemoteContent open() throws IOException {
        log.debug("[S3Origin] s3://{}/{} region={}", bucket, key, region);

        var clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials);
        if (endpointOverride != null) {
            clientBuilder.endpointOverride(endpointOverride);
        }
        S3Client client = clientBuilder.build();

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        ResponseInputStream<GetObjectResponse> response = client.getObject(request);
        GetObjectResponse meta = response.response();

        return RemoteContent.builder()
                .inputStream(response)
                .contentType(meta.contentType())
                .contentLength(meta.contentLength())
                .filename(extractFilename(key))
                .onClose(client::close)
                .build();
    }

    /**
     * Extracts the trailing path segment of the supplied key as the suggested
     * filename for downstream consumers (HTTP responses, file system writes).
     */
    private static String extractFilename(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }

    // ---------------------------------------------------------------------
    //  Fluent builder
    // ---------------------------------------------------------------------

    /**
     * Fluent builder for {@link S3Origin}.
     */
    public static class Builder {
        private String bucket;
        private String key;
        private Region region = Region.US_EAST_1;
        private URI endpointOverride;
        private AwsCredentialsProvider credentials;

        /** Sets the S3 bucket name (required). */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /** Sets the object key (required). */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * Sets the AWS region using its canonical identifier (for example
         * {@code "us-east-1"}). Defaults to {@code us-east-1}.
         */
        public Builder region(String region) {
            this.region = Region.of(region);
            return this;
        }

        /**
         * Sets the AWS region using the SDK's {@link Region} type.
         */
        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        /**
         * Overrides the S3 endpoint. Useful for S3-compatible services such
         * as MinIO, Ceph or LocalStack.
         */
        public Builder endpoint(String endpoint) {
            this.endpointOverride = URI.create(endpoint);
            return this;
        }

        /**
         * Supplies static credentials. When this method is not invoked the
         * default credentials chain is used.
         */
        public Builder credentials(String accessKey, String secretKey) {
            this.credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            );
            return this;
        }

        /**
         * Supplies a custom {@link AwsCredentialsProvider} (for example to
         * assume a role or read from an external vault).
         */
        public Builder credentials(AwsCredentialsProvider provider) {
            this.credentials = provider;
            return this;
        }

        /**
         * @return an immutable {@link S3Origin} configured with this builder
         * @throws IllegalStateException if {@code bucket} or {@code key} is missing
         */
        public S3Origin build() {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalStateException("S3Origin: bucket is required");
            }
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("S3Origin: key is required");
            }
            return new S3Origin(this);
        }
    }
}
