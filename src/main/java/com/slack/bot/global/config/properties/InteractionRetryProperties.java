package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.interaction.retry")
public record InteractionRetryProperties(
        @DefaultValue InboxRetryProperties inbox,
        @DefaultValue OutboxRetryProperties outbox
) {

    public InteractionRetryProperties() {
        this(new InboxRetryProperties(), new OutboxRetryProperties());
    }

    public InteractionRetryProperties {
        if (inbox == null) {
            inbox = new InboxRetryProperties();
        }
        if (outbox == null) {
            outbox = new OutboxRetryProperties();
        }
    }

    public record InboxRetryProperties(
            @DefaultValue("2") int maxAttempts,
            @DefaultValue("100") long initialDelayMs,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("1000") long maxDelayMs
    ) {

        public InboxRetryProperties() {
            this(2, 100L, 2.0, 1000L);
        }
    }

    public record OutboxRetryProperties(
            @DefaultValue("2") int maxAttempts,
            @DefaultValue("100") long initialDelayMs,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("1000") long maxDelayMs
    ) {

        public OutboxRetryProperties() {
            this(2, 100L, 2.0, 1000L);
        }
    }
}
