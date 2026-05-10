package com.github.calcifux.remotedownload;

import java.io.IOException;

/**
 * Universal abstraction for any remote resource that can produce a stream of bytes.
 *
 * <p>Each provider module ({@code remote-download-apache}, {@code remote-download-s3},
 * {@code remote-download-azure}, {@code remote-download-gcs}, ...) supplies its own
 * implementation of this interface. Consumers interact with all sources through
 * the same API regardless of where the bytes actually originate.
 *
 * <p>Provided implementations include:
 * <ul>
 *     <li>{@code HttpOrigin} — JDK {@link java.net.http.HttpClient} based, lightweight</li>
 *     <li>{@code ApacheHttpOrigin} — Apache HttpClient 5, with retries, NTLM, proxy auth</li>
 *     <li>{@code S3Origin} — Amazon S3 via AWS SDK v2</li>
 *     <li>{@code AzureBlobOrigin} — Azure Blob Storage</li>
 *     <li>{@code GcsOrigin} — Google Cloud Storage</li>
 * </ul>
 *
 * <p>Custom sources (FTP, SFTP, Dropbox, SharePoint, ...) can be implemented
 * by providing a single {@link #open()} method.
 *
 * @since 1.0.0
 */
public interface DownloadOrigin {

    /**
     * Opens the underlying resource and returns a {@link RemoteContent} wrapper
     * that exposes a live {@link java.io.InputStream} together with any metadata
     * the implementation could resolve (content type, length, suggested filename).
     *
     * <p>The caller is responsible for closing the returned {@code RemoteContent}
     * — typically with try-with-resources — to release network connections,
     * SDK clients, file handles, and any other underlying resources.
     *
     * @return an open {@link RemoteContent} ready to be consumed
     * @throws IOException if the resource cannot be opened (network failure,
     *                     authentication error, non-success HTTP status, etc.)
     */
    RemoteContent open() throws IOException;
}
