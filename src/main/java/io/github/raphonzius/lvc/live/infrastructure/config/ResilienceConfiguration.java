package io.github.raphonzius.lvc.live.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j Circuit Breaker configuration.
 * Centralizes all circuit breaker settings for the application.
 */
@Slf4j
@Configuration
public class ResilienceConfiguration {

    /**
     * Default circuit breaker configuration.
     * Used as base for all circuit breakers unless overridden.
     */
    @Bean
    public CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)                    // 50% failure rate triggers open
                .slowCallRateThreshold(50.0f)                   // 50% slow calls triggers open
                .slowCallDurationThreshold(Duration.ofSeconds(2)) // Calls > 2s are slow
                .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait 30s before half-open
                .permittedNumberOfCallsInHalfOpenState(3)        // Allow 3 calls in half-open
                .minimumNumberOfCalls(5)                         // Evaluate after 5 calls
                .slidingWindowSize(10)                           // Last 10 calls evaluated
                .recordExceptions(Exception.class)               // Record all exceptions
                .ignoreExceptions()                              // Don't ignore any
                .build();
    }

    /**
     * AIS API circuit breaker configuration.
     * Specific settings for AIS API resilience.
     */
    @Bean
    public CircuitBreakerConfig aisCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .recordExceptions(Exception.class)
                .build();
    }

    /**
     * Circuit breaker registry with event logging.
     * Logs all circuit breaker state changes.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            CircuitBreakerConfig defaultCircuitBreakerConfig,
            CircuitBreakerConfig aisCircuitBreakerConfig
    ) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultCircuitBreakerConfig);

        // Register AIS-specific config
        registry.addConfiguration("ais-api", aisCircuitBreakerConfig);

        // Add event consumer for logging
        registry.getEventPublisher()
                .onEntryAdded(event -> log.info("Circuit breaker registered: {}", event.getAddedEntry().getName()))
                .onEntryRemoved(event -> log.info("Circuit breaker removed: {}", event.getRemovedEntry().getName()));

        // Register a circuit breaker listener for state changes
        registry.getAllCircuitBreakers()
                .forEach(cb -> cb.getEventPublisher()
                        .onStateTransition(event ->
                                log.warn("Circuit breaker '{}' state transition: {} -> {}",
                                        cb.getName(),
                                        event.getStateTransition().getFromState(),
                                        event.getStateTransition().getToState())
                        )
                );

        return registry;
    }
}

