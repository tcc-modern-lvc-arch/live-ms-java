package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import feign.Logger;
import feign.codec.ErrorDecoder;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign configuration for the OlhoVivo API client.
 */
/**
 * Feign configuration for the OlhoVivo API client.
 * Registers the cookie-based auth interceptor, custom error decoder, and log level.
 */
@Configuration
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

    /** Feign log level — {@code BASIC} logs method, URL, status, and elapsed time. */
    @Bean
    Logger.Level olhoVivoFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
