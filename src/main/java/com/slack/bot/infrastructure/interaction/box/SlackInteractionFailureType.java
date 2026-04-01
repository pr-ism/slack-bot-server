package com.slack.bot.infrastructure.interaction.box;

public enum SlackInteractionFailureType {
    NONE,
    PROCESSING_TIMEOUT,
    BUSINESS_INVARIANT,
    RETRY_EXHAUSTED
}
