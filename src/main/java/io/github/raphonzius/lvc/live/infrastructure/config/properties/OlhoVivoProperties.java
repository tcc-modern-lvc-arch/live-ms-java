package io.github.raphonzius.lvc.live.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OlhoVivo (SPTrans) API configuration properties.
 * Loaded from application.yaml under the 'olhovivo' key.
 */
@ConfigurationProperties(prefix = "olhovivo")
public record OlhoVivoProperties(
        String token,
        Polling polling
) {

    public OlhoVivoProperties {
        if (polling == null) {
            polling = new Polling(60000, java.util.List.of());
        }
    }

    /**
     * @param interval  polling interval in milliseconds (default 60000 = 60 seconds)
     * @param lineTerms search terms for /Linha/Buscar — each term may return multiple cl codes
     */
    public record Polling(long interval, java.util.List<String> lineTerms) {

        public Polling {
            if (interval <= 0) {
                interval = 60000;
            }
            if (lineTerms == null) {
                lineTerms = java.util.List.of();
            }
        }
    }
}
