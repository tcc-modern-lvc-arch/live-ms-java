package io.github.raphonzius.lvc.live.infrastructure.input.external;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamTelemetryTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UpstreamTelemetry telemetry = new UpstreamTelemetry(registry);
    private final Logger logger = (Logger) LoggerFactory.getLogger(UpstreamTelemetry.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private static CallNotPermittedException open() {
        return CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("t"));
    }

    @Test
    void circuitOpenIsCountedAndLoggedAsWarnWithoutStack() {
        var reason = telemetry.fallback("ais", open());

        assertThat(reason).isEqualTo("circuit_open");
        assertThat(registry.get("lvc.upstream.fallback").tags("api", "ais", "reason", "circuit_open")
                .counter().count()).isEqualTo(1.0);
        assertThat(appender.list).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getThrowableProxy()).isNull();
        });
    }

    @Test
    void otherFailuresAreCountedByExceptionTypeAndLoggedAsErrorWithStack() {
        telemetry.fallback("cgesp", new IllegalStateException("boom"));
        telemetry.fallback("cgesp", new IllegalStateException("boom"));

        assertThat(registry.get("lvc.upstream.fallback").tags("api", "cgesp", "reason", "IllegalStateException")
                .counter().count()).isEqualTo(2.0);
        assertThat(appender.list).hasSize(2).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void invalidRecordsAreCountedAndWarnedEachTime() {
        telemetry.invalidRecord("ais", "id=null");
        telemetry.invalidRecord("ais", "lat=null");

        assertThat(registry.get("lvc.upstream.invalid_records").tag("api", "ais").counter().count())
                .isEqualTo(2.0);
        assertThat(appender.list).hasSize(2).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
        assertThat(appender.list.get(0).getFormattedMessage()).contains("id=null");
    }
}
