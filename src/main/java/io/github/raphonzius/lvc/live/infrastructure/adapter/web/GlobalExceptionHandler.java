package io.github.raphonzius.lvc.live.infrastructure.adapter.web;

import io.github.raphonzius.lvc.live.application.exception.ApplicationException;
import io.github.raphonzius.lvc.live.domain.exception.DomainException;
import io.github.raphonzius.lvc.live.infrastructure.exception.InfrastructureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

/**
 * Global exception handler for all REST endpoints.
 * Maps the hexagonal exception hierarchy to RFC 9457 {@link ProblemDetail} responses.
 *
 * <p>Each exception root carries its own {@code httpStatus()} — no per-subtype mapping lives here.
 * Adding a new exception subtype requires no change to this class.</p>
 *
 * <ul>
 *   <li>{@link DomainException} → status from {@code ex.httpStatus()} (default 422)</li>
 *   <li>{@link ApplicationException} → status from {@code ex.httpStatus()} (default 500, e.g. 400 for {@code InvalidRangeException})</li>
 *   <li>{@link InfrastructureException} → status from {@code ex.httpStatus()} (502 / 503)</li>
 *   <li>Fallback {@link Exception} → 500 Internal Server Error</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        log.warn("Domain exception: {}", ex.getMessage());
        return build(HttpStatus.valueOf(ex.statusCode()), ex.getMessage(), request);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ProblemDetail> handleApplication(ApplicationException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.statusCode());
        if (status.is4xxClientError()) {
            log.warn("Application exception (client error): {}", ex.getMessage());
        } else {
            log.error("Application exception: {}", ex.getMessage());
        }
        return build(status, ex.getMessage(), request);
    }

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ProblemDetail> handleInfrastructure(InfrastructureException ex, HttpServletRequest request) {
        log.error("Infrastructure exception: {}", ex.getMessage());
        return build(ex.httpStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected exception", ex);
        return build(INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus httpStatus, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(httpStatus, detail);
        problem.setTitle(httpStatus.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(httpStatus).body(problem);
    }
}
