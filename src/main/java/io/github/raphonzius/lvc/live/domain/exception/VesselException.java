package io.github.raphonzius.lvc.live.domain.exception;

/** Domain exception for vessel-related rule violations (e.g. invalid AIS data). Maps to 422. */
public final class VesselException extends DomainException {

    public VesselException(String message) {
        super(message, 422);
    }

    public VesselException(String message, Throwable cause) {
        super(message, cause, 422);
    }
}
