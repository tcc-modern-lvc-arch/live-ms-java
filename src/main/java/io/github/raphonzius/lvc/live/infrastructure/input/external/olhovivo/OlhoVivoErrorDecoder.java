package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import feign.Response;
import feign.codec.ErrorDecoder;
import io.github.raphonzius.lvc.live.infrastructure.exception.OlhoVivoApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign error decoder for OlhoVivo API responses.
 * Maps HTTP error status codes to typed {@link OlhoVivoApiException} instances.
 * On 401, invalidates the cached session so {@link OlhoVivoAuthInterceptor} re-authenticates next request.
 * Delegates unrecognised status codes to the default Feign decoder.
 */
@Slf4j
@RequiredArgsConstructor
public class OlhoVivoErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder delegate = new Default();
    private final OlhoVivoAuthInterceptor authInterceptor;

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("OlhoVivo API error - Method: {}, Status: {}", methodKey, response.status());

        return switch (response.status()) {
            case 401 -> {
                authInterceptor.invalidateSession();
                yield new OlhoVivoApiException("Session expired — re-authenticating", 401);
            }
            case 403 -> new OlhoVivoApiException("OlhoVivo API access forbidden", 403);
            case 404 -> new OlhoVivoApiException("OlhoVivo resource not found", 404);
            case 429 -> new OlhoVivoApiException("OlhoVivo rate limit exceeded", 429);
            case 500, 502, 503, 504 -> new OlhoVivoApiException("OlhoVivo server error", response.status());
            default -> delegate.decode(methodKey, response);
        };
    }
}
