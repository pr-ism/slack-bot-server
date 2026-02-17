package com.slack.bot.infrastructure.interaction.box.in;

public enum SlackInteractionInboxStatus {
    PENDING,
    RETRY_PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
