package io.github.raphonzius.lvc.live.application.exception;

/** Application exception for OlhoVivo (SPTrans) service orchestration failures. */
public final class OlhoVivoServiceException extends ApplicationException {

    public OlhoVivoServiceException(String message) {
        super(message);
    }

    public OlhoVivoServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
