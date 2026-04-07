package io.github.raphonzius.lvc.live.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * OlhoVivo (SPTrans) API configuration properties.
 * Loaded from application.yaml under the 'olhovivo' key.
 */
@ConfigurationProperties(prefix = "olhovivo")
public record OlhoVivoProperties(
        String token,
        Polling polling,
        String baseUrl
) {

    public OlhoVivoProperties {
        if (polling == null) {
            polling = new Polling(60000, Map.of());
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.olhovivo.sptrans.com.br/v2.1";
        }
    }

    /**
     * @param interval  polling interval in milliseconds (default 60000 = 60 seconds)
     * @param lineTerms map of search term → list of tl filter values.
     *                  Key is passed to /Linha/Buscar?termosBusca={key}.
     *                  Values filter the response by the 'tl' (operation mode) field.
     *                  Multiple tl values per term are supported.
     */
    public record Polling(long interval, Map<String, List<Integer>> lineTerms) {

        public Polling {
            if (interval <= 0) {
                interval = 60000;
            }
            if (lineTerms == null) {
                lineTerms = Map.of();
            }
        }
    }
}
