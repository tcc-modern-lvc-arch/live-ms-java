package io.github.raphonzius.lvc.live.infrastructure.input.external.olhovivo;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.raphonzius.lvc.live.infrastructure.config.properties.OlhoVivoProperties;
import io.github.raphonzius.lvc.live.infrastructure.exception.OlhoVivoAuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feign RequestInterceptor that handles OlhoVivo cookie-based session auth.
 *
 * OlhoVivo requires POST /Login/Autenticar?token={token} before any API call.
 * The response sets a session cookie (JSESSIONID) that must be sent on every
 * subsequent request. This interceptor manages authentication lazily and
 * re-authenticates automatically when the session expires.
 *
 * <p>A failed login (request error, token rejected, no Set-Cookie) throws
 * {@link OlhoVivoAuthenticationException} instead of letting the request go out cookie-less
 * (which would 401 and trigger yet another login). After a failure no login is attempted for
 * {@code olhovivo.login-cooldown}; calls in that window fail fast with the same exception
 * without touching the network.</p>
 */
@Slf4j
public class OlhoVivoAuthInterceptor implements RequestInterceptor {

    private final String token;
    private final RestClient restClient;

    private final Clock clock;
    private final Duration loginCooldown;

    private volatile String sessionCookie;
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    /** Guarded by {@code this}: no login is attempted before this instant. */
    private Instant nextLoginAllowedAt = Instant.MIN;

    public OlhoVivoAuthInterceptor(OlhoVivoProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public OlhoVivoAuthInterceptor(OlhoVivoProperties properties, Clock clock) {
        this.clock = clock;
        this.loginCooldown = properties.loginCooldown();
        this.token = properties.token();
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public void apply(RequestTemplate template) {
        if (!authenticated.get()) {
            authenticate();
        }
        if (sessionCookie != null) {
            template.header("Cookie", sessionCookie);
        }
    }

    /**
     * Performs POST /Login/Autenticar?token={token}, captures the Set-Cookie header.
     * Thread-safe: only one thread authenticates at a time.
     *
     * <p>Kept as {@code synchronized} on purpose: since JEP 491 (JDK 24) a virtual thread
     * blocking on I/O inside a monitor no longer pins its carrier, and the lock is only
     * contended during the rare (re)login — the fast path in {@link #apply} is a lock-free
     * {@code AtomicBoolean} read. A lock-free single-flight scheme (shared CompletableFuture)
     * would add complexity without measurable benefit here.</p>
     *
     * @throws OlhoVivoAuthenticationException if the login fails or is in its cooldown window
     */
    public synchronized void authenticate() {
        if (authenticated.get()) {
            return;
        }
        var now = clock.instant();
        if (now.isBefore(nextLoginAllowedAt)) {
            throw new OlhoVivoAuthenticationException("OlhoVivo login skipped (cooldown): previous attempt failed, retry in "
                    + Duration.between(now, nextLoginAllowedAt).toSeconds() + "s");
        }
        try {
            log.info("Authenticating with OlhoVivo API...");
            ResponseEntity<Boolean> response = restClient.post()
                    .uri("/Login/Autenticar?token={token}", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .toEntity(Boolean.class);

            if (!Boolean.TRUE.equals(response.getBody())) {
                throw new OlhoVivoAuthenticationException("OlhoVivo authentication rejected — token may be invalid");
            }
            List<String> cookies = response.getHeaders().get("Set-Cookie");
            if (cookies == null || cookies.isEmpty()) {
                throw new OlhoVivoAuthenticationException("OlhoVivo auth returned true but no Set-Cookie header found");
            }
            sessionCookie = cookies.getFirst().split(";")[0];
            authenticated.set(true);
            nextLoginAllowedAt = Instant.MIN;
            log.info("OlhoVivo authentication successful");
        } catch (OlhoVivoAuthenticationException e) {
            nextLoginAllowedAt = now.plus(loginCooldown);
            log.error("{} — next login attempt in {}s", e.getMessage(), loginCooldown.toSeconds());
            throw e;
        } catch (RuntimeException e) {
            nextLoginAllowedAt = now.plus(loginCooldown);
            log.error("OlhoVivo authentication request failed — next login attempt in {}s", loginCooldown.toSeconds(), e);
            throw new OlhoVivoAuthenticationException("OlhoVivo authentication request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Forces re-authentication on the next request.
     * Call this when a 401 is received.
     */
    public void invalidateSession() {
        authenticated.set(false);
        sessionCookie = null;
        log.info("OlhoVivo session invalidated — will re-authenticate on next request");
    }
}
