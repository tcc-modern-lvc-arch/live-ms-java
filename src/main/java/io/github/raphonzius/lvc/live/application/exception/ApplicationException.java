package io.github.raphonzius.lvc.live.application.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the application exception hierarchy.
 * Thrown when an application-layer service fails to orchestrate domain operations.
 *
 * <p>Each subtype declares the HTTP status it maps to by passing it to the parent constructor.
 * {@code GlobalExceptionHandler} calls {@link #httpStatus()} directly.</p>
 *
 * <p>Sealed — permitted subtypes: {@link AisServiceException}, {@link CgespServiceException},
 * {@link InvalidRangeException}, {@link OlhoVivoServiceException}.
 * Subtypes may pass any status; the default for service failures is 500.</p>
 */
public sealed class ApplicationException extends RuntimeException
        permits AisServiceException, CgespServiceException, InvalidRangeException, OlhoVivoServiceException {

    private final HttpStatus httpStatus;

    public ApplicationException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ApplicationException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP status this exception maps to in the REST response. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
