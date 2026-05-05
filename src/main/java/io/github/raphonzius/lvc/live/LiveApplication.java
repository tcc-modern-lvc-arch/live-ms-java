package io.github.raphonzius.lvc.live;

import io.github.raphonzius.lvc.live.infrastructure.config.properties.AisProperties;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.CgespProperties;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the LVC Live Adapter microservice.
 *
 * <p>Polls AIS (vessel positions) and SPTrans OlhoVivo (bus positions),
 * then streams results to Event Hub via gRPC for downstream consumers.</p>
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableConfigurationProperties({AisProperties.class, CgespProperties.class, OlhoVivoProperties.class})
public class LiveApplication {

    /** Starts the Spring Boot application. */
    static void main(String[] args) {
        SpringApplication.run(LiveApplication.class, args);
    }

}

