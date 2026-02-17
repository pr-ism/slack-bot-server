package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.interactivity.retry")
public record InteractivityRetryProperties(
        @DefaultValue Retry inbox,
        @DefaultValue Retry outbox
) {

    public record Retry(
            @DefaultValue("2") int maxAttempts,
            @DefaultValue("100") long initialDelayMs,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("1000") long maxDelayMs
    ) {
    }
}
