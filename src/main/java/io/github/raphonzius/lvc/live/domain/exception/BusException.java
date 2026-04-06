package io.github.raphonzius.lvc.live.domain.exception;

/** Domain exception for bus-related rule violations (e.g. invalid line, missing stop). */
public final class BusException extends DomainException {

    public BusException(String message) {
        super(message);
    }

    public BusException(String message, Throwable cause) {
        super(message, cause);
    }
}
