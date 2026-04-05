package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom error decoder for AIS API responses.
 * Provides meaningful error handling and logging.
 *
 * Location: infrastructure/input/external/ais/
 */
@Slf4j
public class AisErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder delegate = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("AIS API error - Method: {}, Status: {}, Reason: {}",
                methodKey, response.status(), response.reason());

        return switch (response.status()) {
            case 404 -> new AisApiException("AIS API resource not found", response.status());
            case 429 -> new AisApiException("Rate limit exceeded", response.status());
            case 500, 502, 503, 504 -> new AisApiException("AIS API server error", response.status());
            case 408 -> new AisApiException("Request timeout", response.status());
            default -> delegate.decode(methodKey, response);
        };
    }

    /**
     * Custom exception for AIS API errors.
     */
    @Getter
    public static class AisApiException extends RuntimeException {
        private final int statusCode;

        public AisApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}

