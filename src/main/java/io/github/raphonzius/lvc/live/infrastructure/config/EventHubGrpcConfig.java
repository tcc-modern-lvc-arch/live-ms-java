package io.github.raphonzius.lvc.live.infrastructure.config;

import io.github.raphonzius.lvc.live.infrastructure.config.properties.EventHubProperties;
import io.github.raphonzius.lvc.live.infrastructure.output.grpc.EventPublisher;
import io.github.raphonzius.lvc.live.infrastructure.output.grpc.EventRequestMapper;
import io.github.raphonzius.lvc.live.infrastructure.output.grpc.EventSender;
import io.github.raphonzius.lvc.live.infrastructure.output.grpc.GrpcEventSender;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.time.Clock;

/**
 * EventHub gRPC client wiring on top of Spring Boot's gRPC client support.
 *
 * <p>The channel is the named channel {@value #CHANNEL} resolved by Boot's
 * {@link GrpcChannelFactory} from {@code spring.grpc.client.channel.event-hub.*}
 * (target, keepalive, retry policy as service config). This class only adds what the
 * properties cannot express: explicit {@code enableRetry()}, the shutdown grace period,
 * and the publishing beans.</p>
 *
 * <p>Shutdown: the channel bean is shut down when the context closes, and Boot's channel
 * factory then waits up to {@code event-hub.grpc.shutdown-grace-period} for in-flight calls.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventHubProperties.class)
public class EventHubGrpcConfig {

    /** Name of the Boot-managed channel ({@code spring.grpc.client.channel.event-hub}). */
    public static final String CHANNEL = "event-hub";

    @Bean(destroyMethod = "shutdown")
    @SuppressWarnings("unchecked")
    public ManagedChannel eventHubChannel(GrpcChannelFactory channelFactory, EventHubProperties properties) {
        var options = ChannelBuilderOptions.defaults()
                .withShutdownGracePeriod(properties.grpc().shutdownGracePeriod())
                .withCustomizer(enableRetry());
        return channelFactory.createChannel(CHANNEL, options);
    }

    @Bean
    public EventHubGrpc.EventHubBlockingStub eventHubBlockingStub(ManagedChannel eventHubChannel) {
        return EventHubGrpc.newBlockingStub(eventHubChannel);
    }

    @Bean
    public EventSender eventSender(EventHubGrpc.EventHubBlockingStub stub, EventHubProperties properties) {
        return new GrpcEventSender(stub, properties.grpc().deadline());
    }

    @Bean
    public EventPublisher eventPublisher(EventSender eventSender, EventHubProperties properties,
                                         ObjectProvider<MeterRegistry> registry) {
        var grpc = properties.grpc();
        return new EventPublisher(eventSender, grpc.maxConcurrency(), grpc.batchSize(),
                registry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    @Bean
    public EventRequestMapper eventRequestMapper(EventHubProperties properties) {
        return new EventRequestMapper(properties.grpc().areaId(), Clock.systemUTC());
    }

    /**
     * Retry is on by default in grpc-java; enabled explicitly so the service-config retry policy
     * is never silently ignored. Raw type: the concrete builder type (shaded Netty in production,
     * in-process in tests) is chosen by Boot's channel factory, and Java cannot infer the
     * F-bounded {@code T extends ManagedChannelBuilder<T>} for a lambda.
     */
    @SuppressWarnings("rawtypes")
    private static GrpcChannelBuilderCustomizer enableRetry() {
        return (_, builder) -> builder.enableRetry();
    }
}
