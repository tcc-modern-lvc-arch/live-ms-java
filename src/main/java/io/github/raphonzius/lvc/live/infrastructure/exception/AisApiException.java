package io.github.raphonzius.lvc.live.infrastructure.exception;

import lombok.Getter;

/**
 * Thrown when the AIS Friends external API returns an error response.
 * Carries the upstream HTTP status code for diagnostics.
 */
@Getter
public final class AisApiException extends InfrastructureException {

    /** HTTP status code returned by the AIS API. */
    private final int statusCode;

    /**
     * @param message   description of the API error
     * @param statusCode upstream HTTP status code (e.g. 429, 503)
     */
    public AisApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
