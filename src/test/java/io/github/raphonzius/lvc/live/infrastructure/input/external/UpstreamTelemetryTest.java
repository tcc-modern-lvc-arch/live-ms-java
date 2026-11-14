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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamTelemetryTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock();
    private final UpstreamTelemetry telemetry = new UpstreamTelemetry(registry, clock, Duration.ofMinutes(5));
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
    void otherFailuresAreCountedByExceptionTypeAndLoggedAsErrorWithStackOncePerInterval() {
        telemetry.fallback("cgesp", new IllegalStateException("boom"));
        telemetry.fallback("cgesp", new IllegalStateException("boom"));

        assertThat(registry.get("lvc.upstream.fallback").tags("api", "cgesp", "reason", "IllegalStateException")
                .counter().count()).isEqualTo(2.0);
        assertThat(appender.list).hasSize(2).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.ERROR));
        assertThat(appender.list.get(0).getThrowableProxy()).as("first occurrence has the stack").isNotNull();
        assertThat(appender.list.get(1).getThrowableProxy()).as("repeat is stackless").isNull();

        clock.advance(Duration.ofMinutes(6));
        telemetry.fallback("cgesp", new IllegalStateException("boom"));
        assertThat(appender.list.get(2).getThrowableProxy()).as("after the interval").isNotNull();
    }

    @Test
    void firstFailureAfterASuccessCarriesTheStackAgain() {
        telemetry.fallback("olhovivo", new IllegalStateException("a"));
        telemetry.fallback("olhovivo", new IllegalStateException("b"));
        telemetry.success("olhovivo");
        telemetry.fallback("olhovivo", new IllegalStateException("c"));

        assertThat(appender.list.get(1).getThrowableProxy()).isNull();
        assertThat(appender.list.get(2).getThrowableProxy()).isNotNull();
    }

    @Test
    void stackThrottleIsPerApi() {
        telemetry.fallback("ais", new IllegalStateException("a"));
        telemetry.fallback("cgesp", new IllegalStateException("b"));

        assertThat(appender.list).allSatisfy(e -> assertThat(e.getThrowableProxy()).isNotNull());
    }

    @Test
    void invalidRecordsAreCountedAndWarnedAtMostOncePerInterval() {
        telemetry.invalidRecord("ais", "id=null");
        telemetry.invalidRecord("ais", "lat=null");
        telemetry.invalidRecord("ais", "lon=null");

        assertThat(registry.get("lvc.upstream.invalid_records").tag("api", "ais").counter().count())
                .isEqualTo(3.0);
        assertThat(appender.list).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("id=null");
        });

        clock.advance(Duration.ofMinutes(6));
        telemetry.invalidRecord("ais", "again");
        assertThat(appender.list).hasSize(2);
    }
}
