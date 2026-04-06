package io.github.raphonzius.lvc.live.infrastructure.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the CGESP scraper receives an HTTP error or fails to parse the page.
 * Carries the upstream HTTP status code for diagnostics.
 * Maps to {@link HttpStatus#BAD_GATEWAY} in the REST response.
 */
@Getter
public final class CgespApiException extends InfrastructureException {

    /** HTTP status code returned by the upstream CGESP page (for diagnostics). */
    private final int statusCode;

    public CgespApiException(String message, int statusCode) {
        super(message, HttpStatus.BAD_GATEWAY);
        this.statusCode = statusCode;
    }
}
