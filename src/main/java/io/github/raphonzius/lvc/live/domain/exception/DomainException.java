package io.github.raphonzius.lvc.live.domain.exception;

/**
 * Root of the domain exception hierarchy.
 * Thrown when a domain business rule is violated.
 *
 * <p>Each subtype declares the HTTP status code it maps to by passing it to the parent constructor.
 * {@code GlobalExceptionHandler} (infrastructure layer) calls {@link #statusCode()} and converts
 * to {@code HttpStatus} — keeping Spring Web out of the domain layer.</p>
 *
 * <p>Sealed — permitted subtypes: {@link BusException}, {@link FloodingException}, {@link VesselException}.</p>
 */
public sealed class DomainException extends RuntimeException
        permits BusException, FloodingException, VesselException {

    private final int statusCode;

    public DomainException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public DomainException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status code this exception maps to in the REST response (e.g. 422, 400). */
    public int statusCode() {
        return statusCode;
    }

}
