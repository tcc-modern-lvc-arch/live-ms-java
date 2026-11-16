package io.github.raphonzius.lvc.live.infrastructure.config;

import io.github.raphonzius.lvc.live.infrastructure.output.grpc.EventPublisher;
import io.github.raphonzius.lvc.live.infrastructure.output.grpc.EventRequestMapper;
import io.github.raphonzius.lvc.live.infrastructure.output.grpc.EventSender;
import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.EventHubProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.CompositeChannelFactoryAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring test for {@link EventHubGrpcConfig} on top of Spring Boot's gRPC client
 * auto-configuration, using the same property shape as {@code application.yaml}
 * ({@code static://host:port} target over the shaded Netty transport) against a
 * loopback EventHub stub server.
 */
class EventHubGrpcConfigTest {

    private static final String CHANNEL = "spring.grpc.client.channel.event-hub.";

    private final AtomicInteger attempts = new AtomicInteger();
    private Server server;

    @BeforeEach
    void startHub() throws IOException {
        server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(new EventHubGrpc.EventHubImplBase() {
                    @Override
                    public void sendEvent(EventRequest request, StreamObserver<EventAck> observer) {
                        if (attempts.incrementAndGet() == 1) {
                            observer.onError(Status.UNAVAILABLE.withDescription("warming up").asRuntimeException());
                            return;
                        }
                        observer.onNext(EventAck.newBuilder().setStatus("accepted").build());
                        observer.onCompleted();
                    }
                })
                .build().start();
    }

    @AfterEach
    void stopHub() {
        server.shutdownNow();
    }

    /** Stands in for the application's {@code @ConfigurationPropertiesScan}. */
    @EnableConfigurationProperties(EventHubProperties.class)
    static class Props {
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class,
                        GrpcClientAutoConfiguration.class, CompositeChannelFactoryAutoConfiguration.class))
                .withUserConfiguration(EventHubGrpcConfig.class, Props.class)
                .withPropertyValues(
                        "spring.grpc.client.inprocess.enabled=false",
                        CHANNEL + "target=static://localhost:" + server.getPort(),
                        CHANNEL + "service-config.method[0].name[0].service=event.EventHub",
                        CHANNEL + "service-config.method[0].retry.max-attempts=3",
                        CHANNEL + "service-config.method[0].retry.initial-backoff=10ms",
                        CHANNEL + "service-config.method[0].retry.max-backoff=50ms",
                        CHANNEL + "service-config.method[0].retry.backoff-multiplier=2",
                        CHANNEL + "service-config.method[0].retry.retryable-status-codes=UNAVAILABLE",
                        "event-hub.grpc.area-id=test-area",
                        "event-hub.grpc.deadline=2s",
                        "event-hub.grpc.max-concurrency=8");
    }

    @Test
    void exposesPublishingBeans() {
        runner().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(EventSender.class);
            assertThat(ctx).hasSingleBean(EventPublisher.class);
            assertThat(ctx).hasSingleBean(EventRequestMapper.class);
            assertThat(ctx).hasSingleBean(ManagedChannel.class);
        });
    }

    @Test
    void retriesUnavailableAccordingToServiceConfig() {
        runner().run(ctx -> {
            var ack = ctx.getBean(EventSender.class).send(EventRequest.newBuilder().setEntityId("x").build());

            assertThat(ack.getStatus()).isEqualTo("accepted");
            assertThat(attempts).hasValue(2);
        });
    }

    @Test
    void channelIsShutDownWhenContextCloses() {
        var channel = new ManagedChannel[1];
        runner().run(ctx -> channel[0] = ctx.getBean(ManagedChannel.class));

        assertThat(channel[0].isShutdown()).isTrue();
    }
}
