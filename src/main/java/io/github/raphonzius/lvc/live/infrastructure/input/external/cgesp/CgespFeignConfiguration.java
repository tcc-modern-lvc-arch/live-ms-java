package io.github.raphonzius.lvc.live.infrastructure.input.external.cgesp;

import feign.Logger;
import feign.Response;
import feign.Retryer;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Feign configuration for the CGESP HTML scraper client.
 * Registers a raw-string decoder (bypasses JSON) and a custom error decoder.
 */
public class CgespFeignConfiguration {

    /**
     * Decoder that reads the response body as UTF-8 String regardless of Content-Type.
     * Required because CGESP returns text/html, not application/json.
     */
    @Bean
    public Decoder cgespHtmlDecoder() {
        return (Response response, Type type) -> {
            if (type != String.class) {
                throw new DecodeException(response.status(), "CGESP decoder only supports String return type", response.request());
            }
            if (response.body() == null) {
                return "";
            }
            try (Response.Body body = response.body()) {
                return new String(body.asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new DecodeException(response.status(), "Failed to read CGESP response body", response.request(), e);
            }
        };
    }

    /** Creates the error decoder that maps HTTP errors to {@link CgespApiException}. */
    @Bean
    public ErrorDecoder cgespErrorDecoder() {
        return new CgespErrorDecoder();
    }

    /**
     * Feign-level retry: 3 attempts, 500ms initial backoff, 2s max — executes before
     * the Resilience4j CircuitBreaker sees the outcome, so transient failures don't
     * prematurely open the circuit.
     */
    @Bean
    public Retryer cgespRetryer() {
        return new Retryer.Default(500, 2000, 3);
    }

    /** Feign log level — BASIC logs method, URL, status, and elapsed time. */
    @Bean
    Logger.Level cgespFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
