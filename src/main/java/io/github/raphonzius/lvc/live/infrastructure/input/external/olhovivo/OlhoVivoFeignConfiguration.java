package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import feign.Logger;
import feign.codec.ErrorDecoder;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign configuration for the OlhoVivo API client.
 */
@Configuration
public class OlhoVivoFeignConfiguration {

    @Bean
    public OlhoVivoAuthInterceptor olhoVivoAuthInterceptor(OlhoVivoProperties properties) {
        return new OlhoVivoAuthInterceptor(properties);
    }

    @Bean
    public ErrorDecoder olhoVivoErrorDecoder(OlhoVivoAuthInterceptor authInterceptor) {
        return new OlhoVivoErrorDecoder(authInterceptor);
    }

    @Bean
    Logger.Level olhoVivoFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
