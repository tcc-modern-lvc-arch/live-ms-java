package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.exception.ApplicationException;
import io.github.raphonzius.lvc.live.domain.exception.DomainException;
import io.github.raphonzius.lvc.live.infrastructure.exception.AisApiException;
import io.github.raphonzius.lvc.live.infrastructure.exception.InfrastructureException;
import io.github.raphonzius.lvc.live.infrastructure.exception.OlhoVivoApiException;
import io.github.raphonzius.lvc.live.infrastructure.exception.RedisStreamingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

import static org.springframework.http.HttpStatus.*;

/**
 * Global exception handler for all REST endpoints.
 * Maps the hexagonal exception hierarchy to RFC 9457 {@link ProblemDetail} responses.
 *
 * <ul>
 *   <li>{@link DomainException} → 422 Unprocessable Content</li>
 *   <li>{@link ApplicationException} → 500 Internal Server Error</li>
 *   <li>{@link InfrastructureException} → 502 Bad Gateway / 503 Service Unavailable</li>
 *   <li>Fallback {@link Exception} → 500 Internal Server Error</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles domain-layer rule violations (e.g. invalid bus/vessel state).
     * Returns 422 Unprocessable Content.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        log.warn("Domain exception: {}", ex.getMessage());
        return build(UNPROCESSABLE_CONTENT.value(), ex.getMessage(), request);
    }

    /**
     * Handles application-layer orchestration failures.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ProblemDetail> handleApplication(ApplicationException ex, HttpServletRequest request) {
        log.error("Application exception: {}", ex.getMessage());
        return build(INTERNAL_SERVER_ERROR.value(), ex.getMessage(), request);
    }

    /**
     * Handles infrastructure-layer failures (external APIs, Redis).
     * Status code is derived from the sealed subtype via pattern matching.
     */
    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ProblemDetail> handleInfrastructure(InfrastructureException ex, HttpServletRequest request) {
        log.error("Infrastructure exception: {}", ex.getMessage());
        int statusCode = switch (ex) {
            case AisApiException ignored        -> BAD_GATEWAY.value();
            case OlhoVivoApiException ignored   -> BAD_GATEWAY.value();
            case RedisStreamingException ignored -> SERVICE_UNAVAILABLE.value();
            default                             -> INTERNAL_SERVER_ERROR.value();
        };
        return build(statusCode, ex.getMessage(), request);
    }

    /**
     * Catch-all for any unhandled exception.
     * Returns 500 Internal Server Error with a generic message to avoid leaking internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected exception", ex);
        return build(INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred", request);
    }

    /**
     * Builds an RFC 9457 {@link ProblemDetail} response.
     *
     * @param statusCode HTTP status code
     * @param detail     human-readable explanation for this occurrence
     * @param request    the current HTTP request (used to set {@code instance})
     */
    private ResponseEntity<ProblemDetail> build(int statusCode, String detail, HttpServletRequest request) {
        HttpStatusCode status = HttpStatusCode.valueOf(statusCode);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        HttpStatus resolved = HttpStatus.resolve(statusCode);
        if (resolved != null) {
            problem.setTitle(resolved.getReasonPhrase());
        }
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }
}
