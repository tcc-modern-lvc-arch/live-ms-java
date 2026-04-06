package io.github.raphonzius.lvc.live.domain.exception;

/**
 * Root of the domain exception hierarchy.
 * Thrown when a domain business rule is violated.
 *
 * <p>Sealed — only {@link BusException} and {@link VesselException} are permitted.
 * Handled by {@code GlobalExceptionHandler} → 422 Unprocessable Content.</p>
 */
public sealed class DomainException extends RuntimeException
        permits BusException, VesselException {

    /** @param message human-readable description of the violated rule */
    public DomainException(String message) {
        super(message);
    }

    /** @param message human-readable description of the violated rule
     *  @param cause   the underlying cause */
    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
