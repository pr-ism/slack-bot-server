package com.slack.bot.infrastructure.interaction.box.out;

public enum SlackNotificationOutboxStatus {
    PENDING,
    RETRY_PENDING,
    PROCESSING,
    SENT,
    FAILED
}
