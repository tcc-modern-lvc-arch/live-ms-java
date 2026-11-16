package io.github.raphonzius.lvc.live.infrastructure.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * Root of the infrastructure exception hierarchy.
 * Thrown when an infrastructure adapter fails to communicate with an external system.
 *
 * <p>Each subtype declares the HTTP status it maps to by passing it to the parent constructor.
 * {@code GlobalExceptionHandler} calls {@link #httpStatus()} directly — no per-subtype switch needed.
 * Adding a new sealed subtype automatically forces it to supply its own response status.</p>
 *
 * <p>Sealed — permitted subtypes: {@link AisApiException}, {@link CgespApiException},
 * {@link OlhoVivoApiException}, {@link OlhoVivoAuthenticationException}.</p>
 */
public sealed class InfrastructureException extends RuntimeException
        permits AisApiException, CgespApiException, OlhoVivoApiException, OlhoVivoAuthenticationException {

    private final HttpStatus httpStatus;

    // Flexible constructor bodies (JEP 513): validate and assign before super(...).
    public InfrastructureException(String message, HttpStatus httpStatus) {
        this.httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
        super(message);
    }

    public InfrastructureException(String message, Throwable cause, HttpStatus httpStatus) {
        this.httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
        super(message, cause);
    }

    /** HTTP status this exception maps to in the REST response. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
