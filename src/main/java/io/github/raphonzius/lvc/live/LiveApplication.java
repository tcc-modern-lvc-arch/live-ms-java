package io.github.raphonzius.lvc.live;

import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo.OlhoVivoFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the LVC Live Adapter microservice.
 *
 * <p>Polls SPTrans OlhoVivo bus positions and persists them to PostgreSQL.</p>
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableConfigurationProperties(OlhoVivoProperties.class)
public class LiveApplication {

    /** Starts the Spring Boot application. */
    static void main(String[] args) {
        SpringApplication.run(LiveApplication.class, args);
    }

}
