package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import feign.Response;
import feign.codec.ErrorDecoder;
import io.github.raphonzius.lvc.live.infrastructure.exception.AisApiException;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign error decoder for AIS Friends API responses.
 * Maps HTTP error status codes to typed {@link AisApiException} instances.
 * Delegates unrecognised status codes to the default Feign decoder.
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
}
