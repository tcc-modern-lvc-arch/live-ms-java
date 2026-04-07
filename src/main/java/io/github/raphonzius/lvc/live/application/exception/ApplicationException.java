package io.github.raphonzius.lvc.live.application.exception;

/**
 * Root of the application exception hierarchy.
 * Thrown when an application-layer service fails to orchestrate domain operations.
 *
 * <p>Each subtype declares the HTTP status code it maps to by passing it to the parent constructor.
 * {@code GlobalExceptionHandler} (infrastructure layer) calls {@link #statusCode()} and converts
 * to {@code HttpStatus} — keeping Spring Web out of the application layer.</p>
 *
 * <p>Sealed — permitted subtypes: {@link AisServiceException}, {@link CgespServiceException},
 * {@link InvalidRangeException}, {@link OlhoVivoServiceException}.
 * Subtypes may pass any status; the default for service failures is 500.</p>
 */
public sealed class ApplicationException extends RuntimeException
        permits AisServiceException, CgespServiceException, InvalidRangeException, OlhoVivoServiceException {

    private final int statusCode;

    public ApplicationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApplicationException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status code this exception maps to in the REST response (e.g. 500, 400). */
    public int statusCode() {
        return statusCode;
    }
}
