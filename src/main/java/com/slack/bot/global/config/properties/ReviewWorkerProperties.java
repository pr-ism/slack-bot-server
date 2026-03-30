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
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("30000") long pollCapMs,
            @DefaultValue("30") int batchSize,
            @DefaultValue("60000") long processingTimeoutMs,
            @DefaultValue("100") int timeoutRecoveryBatchSize
    ) {

        public InboxProperties {
            validateWorkerProperties(
                    "inbox",
                    pollDelayMs,
                    pollCapMs,
                    processingTimeoutMs,
                    timeoutRecoveryBatchSize
            );
        }

        public InboxProperties() {
            this(1000L, 30000L, 30, 60000L, 100);
        }
    }

    public record OutboxProperties(
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("30000") long pollCapMs,
            @DefaultValue("50") int batchSize,
            @DefaultValue("60000") long processingTimeoutMs,
            @DefaultValue("100") int timeoutRecoveryBatchSize
    ) {

        public OutboxProperties {
            validateWorkerProperties(
                    "outbox",
                    pollDelayMs,
                    pollCapMs,
                    processingTimeoutMs,
                    timeoutRecoveryBatchSize
            );
        }

        public OutboxProperties() {
            this(1000L, 30000L, 50, 60000L, 100);
        }
    }

    private static void validatePollingProperties(String propertyName, long pollDelayMs, long pollCapMs) {
        if (pollDelayMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".pollDelayMs는 0보다 커야 합니다.");
        }
        if (pollCapMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".pollCapMs는 0보다 커야 합니다.");
        }
        if (pollCapMs < pollDelayMs) {
            throw new IllegalArgumentException(propertyName + ".pollCapMs는 pollDelayMs보다 크거나 같아야 합니다.");
        }
    }

    private static void validateWorkerProperties(
            String propertyName,
            long pollDelayMs,
            long pollCapMs,
            long processingTimeoutMs,
            int timeoutRecoveryBatchSize
    ) {
        validatePollingProperties(propertyName, pollDelayMs, pollCapMs);
        if (processingTimeoutMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".processingTimeoutMs는 0보다 커야 합니다.");
        }
        if (timeoutRecoveryBatchSize <= 0) {
            throw new IllegalArgumentException(propertyName + ".timeoutRecoveryBatchSize는 0보다 커야 합니다.");
        }
    }
}
