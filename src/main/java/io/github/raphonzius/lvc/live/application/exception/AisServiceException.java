package io.github.raphonzius.lvc.live.application.exception;

/** Application exception for AIS service orchestration failures. Maps to 500. */
public final class AisServiceException extends ApplicationException {

    public AisServiceException(String message) {
        super(message, 500);
    }

    public AisServiceException(String message, Throwable cause) {
        super(message, cause, 500);
    }
}
