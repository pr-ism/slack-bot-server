package com.slack.bot.application.interactivity.box;

import lombok.Getter;

@Getter
public enum SlackInteractionIdempotencyScope {
    BLOCK_ACTIONS("block_actions"),
    VIEW_SUBMISSION("view_submission"),
    SLACK_NOTIFICATION_OUTBOX("slack_notification_outbox");

    private final String value;

    SlackInteractionIdempotencyScope(String value) {
        this.value = value;
    }
}
