package io.github.raphonzius.lvc.live.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** {@code lvc.upstream.*}: how often a stack trace is logged per failing upstream. */
@ConfigurationProperties(prefix = "lvc.upstream")
public record UpstreamProperties(@DefaultValue("5m") Duration logStackInterval) {
}
