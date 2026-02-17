package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.interactivity")
public record InteractivityWorkerProperties(
        @DefaultValue Inbox inbox,
        @DefaultValue Outbox outbox
) {

    public InteractivityWorkerProperties() {
        this(new Inbox(), new Outbox());
    }

    public InteractivityWorkerProperties {
        if (inbox == null) {
            inbox = new Inbox();
        }
        if (outbox == null) {
            outbox = new Outbox();
        }
    }

    public record Inbox(
            @DefaultValue BlockActions blockActions,
            @DefaultValue ViewSubmission viewSubmission
    ) {

        public Inbox() {
            this(new BlockActions(), new ViewSubmission());
        }

        public Inbox {
            if (blockActions == null) {
                blockActions = new BlockActions();
            }
            if (viewSubmission == null) {
                viewSubmission = new ViewSubmission();
            }
        }
    }

    public record BlockActions(
            @DefaultValue("true") boolean workerEnabled,
            @DefaultValue("200") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs
    ) {

        public BlockActions() {
            this(true, 200L, 60000L);
        }

        public BlockActions(boolean workerEnabled, long pollDelayMs) {
            this(workerEnabled, pollDelayMs, 60000L);
        }
    }

    public record ViewSubmission(
            @DefaultValue("true") boolean workerEnabled,
            @DefaultValue("200") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs
    ) {

        public ViewSubmission() {
            this(true, 200L, 60000L);
        }

        public ViewSubmission(boolean workerEnabled, long pollDelayMs) {
            this(workerEnabled, pollDelayMs, 60000L);
        }
    }

    public record Outbox(
            @DefaultValue("true") boolean workerEnabled,
            @DefaultValue("200") long pollDelayMs,
            @DefaultValue("60000") long processingTimeoutMs
    ) {

        public Outbox() {
            this(true, 200L, 60000L);
        }

        public Outbox(boolean workerEnabled, long pollDelayMs) {
            this(workerEnabled, pollDelayMs, 60000L);
        }
    }
}
