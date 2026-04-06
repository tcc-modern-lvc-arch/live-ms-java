package io.github.raphonzius.lvc.live.application.exception;

import org.springframework.http.HttpStatus;

/** Application exception for CGESP flooding service failures. Maps to 500. */
public final class CgespServiceException extends ApplicationException {

    public CgespServiceException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public CgespServiceException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
