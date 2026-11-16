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

    // Flexible constructor bodies (JEP 513): the status is validated and the field assigned
    // in the prologue, before super(...) runs — no half-built exception is ever created.
    public DomainException(String message, int statusCode) {
        this.statusCode = requireErrorStatus(statusCode);
        super(message);
    }

    public DomainException(String message, Throwable cause, int statusCode) {
        this.statusCode = requireErrorStatus(statusCode);
        super(message, cause);
    }

    private static int requireErrorStatus(int statusCode) {
        if (statusCode < 400 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be an HTTP error status (4xx/5xx), got " + statusCode);
        }
        return statusCode;
    }

    /** HTTP status code this exception maps to in the REST response (e.g. 422, 400). */
    public int statusCode() {
        return statusCode;
    }

}
