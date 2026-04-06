package io.github.raphonzius.lvc.live.infrastructure.exception;

/** Thrown when publishing to a Redis stream fails (serialization error or connection issue). */
public final class RedisStreamingException extends InfrastructureException {

    public RedisStreamingException(String message) {
        super(message);
    }

    public RedisStreamingException(String message, Throwable cause) {
        super(message, cause);
    }
}
