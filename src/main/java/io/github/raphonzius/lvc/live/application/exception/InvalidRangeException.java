package io.github.raphonzius.lvc.live.application.exception;

/**
 * Thrown when a date range query exceeds the configured maximum.
 * Extends {@link ApplicationException} with status 400 — handled as a client error.
 */
public final class InvalidRangeException extends ApplicationException {

    public InvalidRangeException(String message) {
        super(message, 400);
    }
}
