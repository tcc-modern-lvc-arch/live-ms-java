package io.github.raphonzius.lvc.live.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for JSON serialization/deserialization.
 */
@Configuration
public class JacksonConfiguration {

    /**
     * Configures the global {@link ObjectMapper}.
     * Disables {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} so date/time values
     * are serialized as ISO-8601 strings rather than epoch numbers.
     *
     * @return configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

