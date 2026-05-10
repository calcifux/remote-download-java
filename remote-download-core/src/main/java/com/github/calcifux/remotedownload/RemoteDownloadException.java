package com.github.calcifux.remotedownload;

/**
 * Unchecked exception thrown by the library to signal failures that originate
 * inside {@link DownloadOrigin} implementations or while consuming a
 * {@link RemoteContent} — network errors, authentication failures, non-success
 * HTTP statuses, malformed responses, and any other condition that prevents
 * the requested resource from being delivered.
 *
 * <p>Wraps the original cause whenever one is available so that callers can
 * inspect it through {@link Throwable#getCause()}.
 *
 * @since 1.0.0
 */
public class RemoteDownloadException extends RuntimeException {

    /**
     * Creates a new exception with the supplied message and no cause.
     *
     * @param message human-readable description of the failure
     */
    public RemoteDownloadException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with both a message and an underlying cause.
     *
     * @param message human-readable description of the failure
     * @param cause   original throwable that triggered this exception
     */
    public RemoteDownloadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception that delegates its message to the supplied cause.
     *
     * @param cause original throwable that triggered this exception
     */
    public RemoteDownloadException(Throwable cause) {
        super(cause);
    }
}
