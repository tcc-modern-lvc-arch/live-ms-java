package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import com.sun.net.httpserver.HttpServer;
import feign.RequestTemplate;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import io.github.raphonzius.lvc.live.infrastructure.exception.OlhoVivoAuthenticationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OlhoVivoAuthInterceptorTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private HttpServer server;
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>("false");
    private final AtomicReference<String> setCookie = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/Login/Autenticar", exchange -> {
            logins.incrementAndGet();
            var bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (setCookie.get() != null) {
                exchange.getResponseHeaders().add("Set-Cookie", setCookie.get());
            }
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OlhoVivoAuthInterceptor interceptor() {
        var properties = new OlhoVivoProperties("tok", null, "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(60));
        return new OlhoVivoAuthInterceptor(properties, clock);
    }

    private static String cookieOf(RequestTemplate template) {
        var values = template.headers().get("Cookie");
        return values == null ? null : values.iterator().next();
    }

    @Test
    void successfulLoginSendsTheSessionCookieAndDoesNotLoginAgain() {
        body.set("true");
        setCookie.set("JSESSIONID=abc; Path=/");
        var interceptor = interceptor();
        var first = new RequestTemplate();
        var second = new RequestTemplate();

        interceptor.apply(first);
        interceptor.apply(second);

        assertThat(cookieOf(first)).isEqualTo("JSESSIONID=abc");
        assertThat(cookieOf(second)).isEqualTo("JSESSIONID=abc");
        assertThat(logins).hasValue(1);
    }

    @Test
    void rejectedTokenThrowsAuthenticationException() {
        body.set("false");

        assertThatThrownBy(() -> interceptor().apply(new RequestTemplate()))
                .isInstanceOf(OlhoVivoAuthenticationException.class)
                .hasMessageContaining("token");
    }

    @Test
    void missingSetCookieThrowsAuthenticationException() {
        body.set("true");
        setCookie.set(null);

        assertThatThrownBy(() -> interceptor().apply(new RequestTemplate()))
                .isInstanceOf(OlhoVivoAuthenticationException.class)
                .hasMessageContaining("Set-Cookie");
    }

    @Test
    void loginRequestErrorIsWrappedWithItsCause() {
        status.set(500);

        assertThatThrownBy(() -> interceptor().apply(new RequestTemplate()))
                .isInstanceOf(OlhoVivoAuthenticationException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void afterAFailedLoginNoNewLoginIsAttemptedDuringTheCooldown() {
        var interceptor = interceptor();

        assertThatThrownBy(() -> interceptor.apply(new RequestTemplate()))
                .isInstanceOf(OlhoVivoAuthenticationException.class);
        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> interceptor.apply(new RequestTemplate()))
                .isInstanceOf(OlhoVivoAuthenticationException.class)
                .hasMessageContaining("cooldown");
        assertThatThrownBy(() -> interceptor.apply(new RequestTemplate()))
                .isInstanceOf(OlhoVivoAuthenticationException.class);

        assertThat(logins).as("only the first attempt reached the server").hasValue(1);
    }

    @Test
    void loginIsRetriedOnceTheCooldownElapsedAndRecovers() {
        var interceptor = interceptor();
        assertThatThrownBy(() -> interceptor.apply(new RequestTemplate()))
                .isInstanceOf(OlhoVivoAuthenticationException.class);

        clock.advance(Duration.ofSeconds(61));
        body.set("true");
        setCookie.set("JSESSIONID=ok; Path=/");
        var template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(logins).hasValue(2);
        assertThat(cookieOf(template)).isEqualTo("JSESSIONID=ok");
    }

    @Test
    void invalidatedSessionRelogsInImmediatelyAfterASuccessfulLogin() {
        body.set("true");
        setCookie.set("JSESSIONID=a; Path=/");
        var interceptor = interceptor();
        interceptor.apply(new RequestTemplate());

        interceptor.invalidateSession();
        interceptor.apply(new RequestTemplate());

        assertThat(logins).hasValue(2);
    }
}
