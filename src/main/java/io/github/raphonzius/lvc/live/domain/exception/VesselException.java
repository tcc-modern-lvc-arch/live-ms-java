package io.github.raphonzius.lvc.live.domain.exception;

import org.springframework.http.HttpStatus;

/** Domain exception for vessel-related rule violations (e.g. invalid AIS data). */
public final class VesselException extends DomainException {

    public VesselException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public VesselException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
