package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import feign.codec.ErrorDecoder;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for the OlhoVivo API client.
 * Registers the cookie-based auth interceptor, and custom error decoder (neither can be a property: the decoder needs the interceptor instance).
 */
public class OlhoVivoFeignConfiguration {

    /** Creates the auth interceptor that manages the OlhoVivo JSESSIONID cookie. */
    @Bean
    public OlhoVivoAuthInterceptor olhoVivoAuthInterceptor(OlhoVivoProperties properties) {
        return new OlhoVivoAuthInterceptor(properties);
    }

    /** Creates the error decoder that maps API errors to {@link io.github.raphonzius.lvc.live.infrastructure.exception.OlhoVivoApiException}. */
    @Bean
    public ErrorDecoder olhoVivoErrorDecoder(OlhoVivoAuthInterceptor authInterceptor) {
        return new OlhoVivoErrorDecoder(authInterceptor);
    }
}
