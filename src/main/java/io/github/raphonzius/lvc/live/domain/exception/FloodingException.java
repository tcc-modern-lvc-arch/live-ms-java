package io.github.raphonzius.lvc.live.domain.exception;

/** Domain exception for flooding-related rule violations. Maps to 422. */
public final class FloodingException extends DomainException {

    public FloodingException(String message) {
        super(message, 422);
    }

    public FloodingException(String message, Throwable cause) {
        super(message, cause, 422);
    }
}
