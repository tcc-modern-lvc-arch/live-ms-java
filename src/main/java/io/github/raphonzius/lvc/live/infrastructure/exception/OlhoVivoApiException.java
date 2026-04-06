package io.github.raphonzius.lvc.live.infrastructure.exception;

import lombok.Getter;

/**
 * Thrown when the SPTrans OlhoVivo API returns an error response.
 * Carries the upstream HTTP status code for diagnostics.
 */
@Getter
public final class OlhoVivoApiException extends InfrastructureException {

    /** HTTP status code returned by the OlhoVivo API. */
    private final int statusCode;

    /**
     * @param message    description of the API error
     * @param statusCode upstream HTTP status code (e.g. 401, 403, 429)
     */
    public OlhoVivoApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
