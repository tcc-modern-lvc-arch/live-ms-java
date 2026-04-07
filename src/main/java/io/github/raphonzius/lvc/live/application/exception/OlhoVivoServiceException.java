package io.github.raphonzius.lvc.live.application.exception;

/** Application exception for OlhoVivo (SPTrans) service orchestration failures. Maps to 500. */
public final class OlhoVivoServiceException extends ApplicationException {

    public OlhoVivoServiceException(String message) {
        super(message, 500);
    }

    public OlhoVivoServiceException(String message, Throwable cause) {
        super(message, cause, 500);
    }
}
