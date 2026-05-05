package io.github.raphonzius.lvc.live.infrastructure.config;

import io.github.raphonzius.lvc.live.infrastructure.config.properties.EventHubProperties;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client configuration for the EventHub service.
 * Provides the EventHubGrpc.EventHubBlockingStub for publishing events.
 *
 * Uses Java 21+ patterns:
 * - ConfigurationProperties record for immutable config
 * - Type inference (var)
 * - Record accessors instead of getter methods
 */
@Configuration
@EnableConfigurationProperties(EventHubProperties.class)
public class EventHubGrpcConfig {

    private final EventHubProperties properties;

    public EventHubGrpcConfig(EventHubProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ManagedChannel eventHubChannel() {
        var grpc = properties.grpc();
        return ManagedChannelBuilder
                .forAddress(grpc.host(), grpc.port())
                .usePlaintext()
                .keepAliveTime(grpc.keepAlive().toSeconds(), TimeUnit.SECONDS)
                .keepAliveTimeout(grpc.keepAliveTimeout().toSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public EventHubGrpc.EventHubBlockingStub eventHubBlockingStub(ManagedChannel eventHubChannel) {
        return EventHubGrpc.newBlockingStub(eventHubChannel);
    }
}
