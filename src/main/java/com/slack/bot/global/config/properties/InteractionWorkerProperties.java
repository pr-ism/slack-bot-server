package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.interaction")
public record InteractionWorkerProperties(
        @DefaultValue InboxProperties inbox,
        @DefaultValue OutboxProperties outbox
) {

    public InteractionWorkerProperties() {
        this(new InboxProperties(), new OutboxProperties());
    }

    public InteractionWorkerProperties {
        if (inbox == null) {
            inbox = new InboxProperties();
        }
        if (outbox == null) {
            outbox = new OutboxProperties();
        }
    }

    public record InboxProperties(
            @DefaultValue BlockActionsProperties blockActions,
            @DefaultValue ViewSubmissionProperties viewSubmission
    ) {

        public InboxProperties() {
            this(new BlockActionsProperties(), new ViewSubmissionProperties());
        }

        public InboxProperties {
            if (blockActions == null) {
                blockActions = new BlockActionsProperties();
            }
            if (viewSubmission == null) {
                viewSubmission = new ViewSubmissionProperties();
            }
        }
    }

    public record BlockActionsProperties(
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs,
            @DefaultValue("30000") long pollCapMs,
            @DefaultValue("100") int timeoutRecoveryBatchSize
    ) {

        public BlockActionsProperties {
            validateInboxPollingProperties(
                    "blockActions",
                    pollDelayMs,
                    processingTimeoutMs,
                    pollCapMs,
                    timeoutRecoveryBatchSize
            );
        }

        public BlockActionsProperties() {
            this(1000L, 60000L, 30000L, 100);
        }

        public BlockActionsProperties(long pollDelayMs, long processingTimeoutMs) {
            this(pollDelayMs, processingTimeoutMs, 30000L, 100);
        }

        public BlockActionsProperties(long pollDelayMs, long processingTimeoutMs, long pollCapMs) {
            this(pollDelayMs, processingTimeoutMs, pollCapMs, 100);
        }
    }

    public record ViewSubmissionProperties(
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs,
            @DefaultValue("30000") long pollCapMs,
            @DefaultValue("100") int timeoutRecoveryBatchSize
    ) {

        public ViewSubmissionProperties {
            validateInboxPollingProperties(
                    "viewSubmission",
                    pollDelayMs,
                    processingTimeoutMs,
                    pollCapMs,
                    timeoutRecoveryBatchSize
            );
        }

        public ViewSubmissionProperties() {
            this(1000L, 60000L, 30000L, 100);
        }

        public ViewSubmissionProperties(long pollDelayMs, long processingTimeoutMs) {
            this(pollDelayMs, processingTimeoutMs, 30000L, 100);
        }

        public ViewSubmissionProperties(long pollDelayMs, long processingTimeoutMs, long pollCapMs) {
            this(pollDelayMs, processingTimeoutMs, pollCapMs, 100);
        }
    }

    public record OutboxProperties(
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs,
            @DefaultValue("30000") long pollCapMs,
            @DefaultValue("100") int timeoutRecoveryBatchSize
    ) {

        public OutboxProperties {
            validateInboxPollingProperties(
                    "outbox",
                    pollDelayMs,
                    processingTimeoutMs,
                    pollCapMs,
                    timeoutRecoveryBatchSize
            );
        }

        public OutboxProperties() {
            this(1000L, 60000L, 30000L, 100);
        }

        public OutboxProperties(long pollDelayMs, long processingTimeoutMs) {
            this(pollDelayMs, processingTimeoutMs, 30000L, 100);
        }

        public OutboxProperties(long pollDelayMs, long processingTimeoutMs, long pollCapMs) {
            this(pollDelayMs, processingTimeoutMs, pollCapMs, 100);
        }
    }

    private static void validateInboxPollingProperties(
            String propertyName,
            long pollDelayMs,
            long processingTimeoutMs,
            long pollCapMs,
            int timeoutRecoveryBatchSize
    ) {
        if (pollDelayMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".pollDelayMs는 0보다 커야 합니다.");
        }
        if (processingTimeoutMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".processingTimeoutMs는 0보다 커야 합니다.");
        }
        if (pollCapMs < pollDelayMs) {
            throw new IllegalArgumentException(propertyName + ".pollCapMs는 pollDelayMs보다 크거나 같아야 합니다.");
        }
        if (timeoutRecoveryBatchSize <= 0) {
            throw new IllegalArgumentException(propertyName + ".timeoutRecoveryBatchSize는 0보다 커야 합니다.");
        }
    }

    private static void validatePollingProperties(
            String propertyName,
            long pollDelayMs,
            long processingTimeoutMs,
            long pollCapMs
    ) {
        if (pollDelayMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".pollDelayMs는 0보다 커야 합니다.");
        }
        if (processingTimeoutMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".processingTimeoutMs는 0보다 커야 합니다.");
        }
        if (pollCapMs <= 0L) {
            throw new IllegalArgumentException(propertyName + ".pollCapMs는 0보다 커야 합니다.");
        }
        if (pollCapMs < pollDelayMs) {
            throw new IllegalArgumentException(propertyName + ".pollCapMs는 pollDelayMs보다 크거나 같아야 합니다.");
        }
    }
}
