package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign configuration for AIS API client.
 * Circuit breaker is handled by Spring Cloud Circuit Breaker starter.
 *
 * Location: infrastructure/input/external/ais/
 */
@Configuration
public class AisFeignConfiguration {

    /**
     * Feign logger level.
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom error decoder for handling AIS API errors.
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new AisErrorDecoder();
    }
}

