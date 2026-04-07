package io.github.raphonzius.lvc.live.application.exception;

/** Application exception for CGESP flooding service failures. Maps to 500. */
public final class CgespServiceException extends ApplicationException {

    public CgespServiceException(String message) {
        super(message, 500);
    }

    public CgespServiceException(String message, Throwable cause) {
        super(message, cause, 500);
    }
}
