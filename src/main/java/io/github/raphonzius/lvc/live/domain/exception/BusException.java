package io.github.raphonzius.lvc.live.domain.exception;

/** Domain exception for bus-related rule violations (e.g. invalid line, missing stop). Maps to 422. */
public final class BusException extends DomainException {

    public BusException(String message) {
        super(message, 422);
    }

    public BusException(String message, Throwable cause) {
        super(message, cause, 422);
    }
}
