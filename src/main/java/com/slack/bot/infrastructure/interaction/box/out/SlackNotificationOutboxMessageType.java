package com.slack.bot.infrastructure.interaction.box.out;

public enum SlackNotificationOutboxMessageType {
    EPHEMERAL_TEXT,
    EPHEMERAL_BLOCKS,
    CHANNEL_TEXT,
    CHANNEL_BLOCKS
}
