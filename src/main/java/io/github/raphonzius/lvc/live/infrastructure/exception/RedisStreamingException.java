package io.github.raphonzius.lvc.live.infrastructure.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when publishing to a Redis stream fails (serialization error or connection issue).
 * Maps to {@link HttpStatus#SERVICE_UNAVAILABLE} in the REST response.
 */
public final class RedisStreamingException extends InfrastructureException {

    public RedisStreamingException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public RedisStreamingException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
