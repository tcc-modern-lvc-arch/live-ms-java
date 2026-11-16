package io.github.raphonzius.lvc.live.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Publishing settings for the EventHub gRPC client ({@code event-hub.grpc.*}).
 *
 * <p>Transport settings (target, keepalive, retry service config) are Spring Boot's
 * {@code spring.grpc.client.channel.event-hub.*}; {@code event-hub.grpc.host/port} remain in
 * application.yaml only as inputs to that target (so {@code EVENT_HUB_GRPC_HOST} keeps working).</p>
 */
@ConfigurationProperties(prefix = "event-hub")
public record EventHubProperties(Grpc grpc) {

    public EventHubProperties {
        grpc = grpc != null ? grpc : new Grpc(null, null, 0, 0, null);
    }

    /**
     * @param areaId              routing key sent in every {@code EventRequest}
     * @param deadline            per-call deadline for {@code SendEvent} (retries happen within it)
     * @param maxConcurrency      maximum in-flight {@code SendEvent} RPCs across all streams
     * @param batchSize           window size used to slice large batches
     * @param shutdownGracePeriod time to wait for in-flight RPCs when the channel shuts down
     */
    public record Grpc(
            String areaId,
            Duration deadline,
            int maxConcurrency,
            int batchSize,
            Duration shutdownGracePeriod
    ) {
        public Grpc {
            areaId = areaId != null ? areaId : "default-area";
            deadline = deadline != null ? deadline : Duration.ofSeconds(2);
            maxConcurrency = maxConcurrency > 0 ? maxConcurrency : 32;
            batchSize = batchSize > 0 ? batchSize : 500;
            shutdownGracePeriod = shutdownGracePeriod != null ? shutdownGracePeriod : Duration.ofSeconds(5);
        }
    }
}
