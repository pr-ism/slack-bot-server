package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "review.notification")
public record ReviewWorkerProperties(
        @DefaultValue InboxProperties inbox,
        @DefaultValue OutboxProperties outbox
) {

    public ReviewWorkerProperties() {
        this(new InboxProperties(), new OutboxProperties());
    }

    public ReviewWorkerProperties {
        if (inbox == null) {
            inbox = new InboxProperties();
        }
        if (outbox == null) {
            outbox = new OutboxProperties();
        }
    }

    public record InboxProperties(
            @DefaultValue("true") boolean workerEnabled,
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("30") int batchSize,
            @DefaultValue("60000") long processingTimeoutMs
    ) {

        public InboxProperties() {
            this(true, 1000L, 30, 60000L);
        }
    }

    public record OutboxProperties(
            @DefaultValue("true") boolean workerEnabled,
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("50") int batchSize,
            @DefaultValue("60000") long processingTimeoutMs
    ) {

        public OutboxProperties() {
            this(true, 1000L, 50, 60000L);
        }
    }
}
