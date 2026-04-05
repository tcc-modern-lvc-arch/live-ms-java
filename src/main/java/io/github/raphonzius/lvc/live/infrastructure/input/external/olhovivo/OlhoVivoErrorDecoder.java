package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom error decoder for OlhoVivo API responses.
 */
@Slf4j
public class OlhoVivoErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder delegate = new Default();
    private final OlhoVivoAuthInterceptor authInterceptor;

    public OlhoVivoErrorDecoder(OlhoVivoAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

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

    @Getter
    public static class OlhoVivoApiException extends RuntimeException {
        private final int statusCode;

        public OlhoVivoApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
