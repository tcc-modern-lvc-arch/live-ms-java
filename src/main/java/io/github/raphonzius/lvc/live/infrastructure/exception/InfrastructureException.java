package io.github.raphonzius.lvc.live.infrastructure.exception;

/**
 * Root of the infrastructure exception hierarchy.
 * Thrown when an infrastructure adapter fails to communicate with an external system.
 *
 * <p>Sealed — only {@link AisApiException}, {@link OlhoVivoApiException},
 * and {@link RedisStreamingException} are permitted.
 * Handled by {@code GlobalExceptionHandler} → 502 / 503 depending on subtype.</p>
 */
public sealed class InfrastructureException extends RuntimeException
        permits AisApiException, OlhoVivoApiException, RedisStreamingException {

    /** @param message description of the infrastructure failure */
    public InfrastructureException(String message) {
        super(message);
    }

    /** @param message description of the infrastructure failure
     *  @param cause   the underlying cause */
    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
