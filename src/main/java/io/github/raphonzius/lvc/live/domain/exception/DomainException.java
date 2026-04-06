package io.github.raphonzius.lvc.live.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the domain exception hierarchy.
 * Thrown when a domain business rule is violated.
 *
 * <p>Each subtype declares the HTTP status it maps to by passing it to the parent constructor.
 * {@code GlobalExceptionHandler} calls {@link #httpStatus()} directly.</p>
 *
 * <p>Sealed — permitted subtypes: {@link BusException}, {@link FloodingException}, {@link VesselException}.</p>
 */
public sealed class DomainException extends RuntimeException
        permits BusException, FloodingException, VesselException {

    private final HttpStatus httpStatus;

    public DomainException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public DomainException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP status this exception maps to in the REST response. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
