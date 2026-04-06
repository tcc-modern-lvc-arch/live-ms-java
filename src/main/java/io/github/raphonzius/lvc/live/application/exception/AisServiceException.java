package io.github.raphonzius.lvc.live.application.exception;

/** Application exception for AIS service orchestration failures. */
public final class AisServiceException extends ApplicationException {

    public AisServiceException(String message) {
        super(message);
    }

    public AisServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
