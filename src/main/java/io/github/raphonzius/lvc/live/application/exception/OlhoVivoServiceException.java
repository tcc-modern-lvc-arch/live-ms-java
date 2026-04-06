package io.github.raphonzius.lvc.live.application.exception;

import org.springframework.http.HttpStatus;

/** Application exception for OlhoVivo (SPTrans) service orchestration failures. Maps to 500. */
public final class OlhoVivoServiceException extends ApplicationException {

    public OlhoVivoServiceException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public OlhoVivoServiceException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
