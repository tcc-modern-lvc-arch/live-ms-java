package io.github.raphonzius.lvc.live.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AIS configuration properties.
 * Loaded from application.yaml under the 'ais' key.
 *
 * Record-based configuration for immutability and clarity.
 */
@ConfigurationProperties(prefix = "ais")
public record AisProperties(
        Polling polling
) {

    /**
     * Creates AisProperties with default polling interval.
     */
    public AisProperties {
        if (polling == null) {
            polling = new Polling(30000);
        }
    }

    /**
     * Polling configuration record.
     *
     * @param interval Polling interval in milliseconds (default: 30000 = 30 seconds)
     */
    public record Polling(long interval) {

        /**
         * Creates Polling with default interval.
         */
        public Polling {
            if (interval <= 0) {
                interval = 30000;
            }
        }
    }
}



