package io.github.raphonzius.lvc.live.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for EventHub gRPC client.
 * Uses Java record with Spring Boot 3.2+ @ConfigurationProperties support.
 *
 * Binds to: event-hub.grpc.* in application.yaml
 */
@ConfigurationProperties(prefix = "event-hub")
public record EventHubProperties(Grpc grpc) {

    /**
     * gRPC connection configuration.
     */
    public record Grpc(
            String host,
            int port,
            Duration keepAlive,
            Duration keepAliveTimeout,
            String areaId
    ) {
        // Compact constructor with defaults
        public Grpc {
            host = host != null ? host : "localhost";
            port = port != 0 ? port : 50051;
            keepAlive = keepAlive != null ? keepAlive : Duration.ofSeconds(30);
            keepAliveTimeout = keepAliveTimeout != null ? keepAliveTimeout : Duration.ofSeconds(10);
            areaId = areaId != null ? areaId : "default-area";
        }
    }
}
