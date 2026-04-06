package io.github.raphonzius.lvc.live.infrastructure.input.external.cgesp;

import feign.Response;
import feign.codec.ErrorDecoder;
import io.github.raphonzius.lvc.live.infrastructure.exception.CgespApiException;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign error decoder for CGESP HTTP responses.
 * Maps HTTP error status codes to typed {@link CgespApiException} instances.
 */
@Slf4j
public class CgespErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder delegate = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("CGESP error — method: {}, status: {}", methodKey, response.status());

        return switch (response.status()) {
            case 403 -> new CgespApiException("CGESP access forbidden", 403);
            case 404 -> new CgespApiException("CGESP resource not found", 404);
            case 429 -> new CgespApiException("CGESP rate limit exceeded", 429);
            case 500, 502, 503, 504 -> new CgespApiException("CGESP server error", response.status());
            default -> delegate.decode(methodKey, response);
        };
    }
}
