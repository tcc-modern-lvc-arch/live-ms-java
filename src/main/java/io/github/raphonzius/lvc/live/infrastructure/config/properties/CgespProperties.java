package io.github.raphonzius.lvc.live.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CGESP flooding scraper configuration properties.
 * Loaded from application.yaml under the 'cgesp' key.
 */
@ConfigurationProperties(prefix = "cgesp")
public record CgespProperties(Polling polling, int maxRangeDays) {

    public CgespProperties {
        if (polling == null) polling = new Polling(60000);
    }

    /**
     * @param interval polling interval in milliseconds (default 60000 = 1 minute)
     */
    public record Polling(long interval) {

        public Polling {
            if (interval <= 0) interval = 60000;
        }
    }
}
