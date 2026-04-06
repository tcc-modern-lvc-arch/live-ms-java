package io.github.raphonzius.lvc.live.application.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a date range query exceeds the configured maximum.
 * Extends {@link ApplicationException} with status 400 — the more-specific
 * {@code @ExceptionHandler} in {@code GlobalExceptionHandler} handles it as a client error.
 */
public final class InvalidRangeException extends ApplicationException {

    public InvalidRangeException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
