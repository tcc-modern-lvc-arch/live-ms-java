package io.github.raphonzius.lvc.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
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
@ConfigurationPropertiesScan
public class LiveApplication {

    /** Starts the Spring Boot application. */
    static void main(String[] args) {
        SpringApplication.run(LiveApplication.class, args);
    }

}

