package io.github.raphonzius.lvc.live.infrastructure.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the SPTrans OlhoVivo API returns an error response.
 * Carries the upstream HTTP status code for diagnostics.
 * Maps to {@link HttpStatus#BAD_GATEWAY} in the REST response.
 */
@Getter
public final class OlhoVivoApiException extends InfrastructureException {

    /** HTTP status code returned by the upstream OlhoVivo API (for diagnostics). */
    private final int statusCode;

    public OlhoVivoApiException(String message, int statusCode) {
        super(message, HttpStatus.BAD_GATEWAY);
        this.statusCode = statusCode;
    }
}
