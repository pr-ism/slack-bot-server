package com.slack.bot.infrastructure.interaction.box;

public enum SlackInteractionFailureType {
    ABSENT,
    NONE,
    RETRYABLE,
    PROCESSING_TIMEOUT,
    BUSINESS_INVARIANT,
    RETRY_EXHAUSTED;

    public boolean isRetryPendingOutboxFailureType() {
        return this == RETRYABLE || this == PROCESSING_TIMEOUT;
    }

    public boolean isFailedOutboxFailureType() {
        return this == BUSINESS_INVARIANT || this == RETRY_EXHAUSTED;
    }
}
