package io.github.raphonzius.lvc.live.domain.exception;

import org.springframework.http.HttpStatus;

/** Domain exception for flooding-related rule violations. */
public final class FloodingException extends DomainException {

    public FloodingException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public FloodingException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
