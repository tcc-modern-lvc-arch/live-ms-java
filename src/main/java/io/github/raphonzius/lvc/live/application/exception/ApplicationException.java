package io.github.raphonzius.lvc.live.application.exception;

/**
 * Root of the application exception hierarchy.
 * Thrown when an application-layer service fails to orchestrate domain operations.
 *
 * <p>Sealed — only {@link AisServiceException} and {@link OlhoVivoServiceException} are permitted.
 * Handled by {@code GlobalExceptionHandler} → 500 Internal Server Error.</p>
 */
public sealed class ApplicationException extends RuntimeException
        permits AisServiceException, OlhoVivoServiceException {

    /** @param message description of the service failure */
    public ApplicationException(String message) {
        super(message);
    }

    /** @param message description of the service failure
     *  @param cause   the underlying cause */
    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
