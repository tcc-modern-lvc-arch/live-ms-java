package io.github.raphonzius.lvc.live.infrastructure.input.external.ais;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Feign configuration for AIS API client.
 * Circuit breaker is handled by Spring Cloud Circuit Breaker starter.
 */
public class AisFeignConfiguration {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new AisErrorDecoder();
    }

    @Bean
    public RequestInterceptor browserHeadersInterceptor() {
        return template -> template
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.aisfriends.com/");
    }
}

