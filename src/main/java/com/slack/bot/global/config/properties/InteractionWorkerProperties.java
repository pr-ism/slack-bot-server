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
            @DefaultValue("30000") long pollCapMs
    ) {

        public BlockActionsProperties() {
            this(1000L, 60000L, 30000L);
        }

        public BlockActionsProperties(long pollDelayMs, long processingTimeoutMs) {
            this(pollDelayMs, processingTimeoutMs, 30000L);
        }
    }

    public record ViewSubmissionProperties(
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs,
            @DefaultValue("30000") long pollCapMs
    ) {

        public ViewSubmissionProperties() {
            this(1000L, 60000L, 30000L);
        }

        public ViewSubmissionProperties(long pollDelayMs, long processingTimeoutMs) {
            this(pollDelayMs, processingTimeoutMs, 30000L);
        }
    }

    public record OutboxProperties(
            @DefaultValue("1000") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs,
            @DefaultValue("30000") long pollCapMs
    ) {

        public OutboxProperties() {
            this(1000L, 60000L, 30000L);
        }

        public OutboxProperties(long pollDelayMs, long processingTimeoutMs) {
            this(pollDelayMs, processingTimeoutMs, 30000L);
        }
    }
}
