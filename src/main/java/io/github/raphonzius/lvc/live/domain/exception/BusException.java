package io.github.raphonzius.lvc.live.domain.exception;

import org.springframework.http.HttpStatus;

/** Domain exception for bus-related rule violations (e.g. invalid line, missing stop). */
public final class BusException extends DomainException {

    public BusException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
