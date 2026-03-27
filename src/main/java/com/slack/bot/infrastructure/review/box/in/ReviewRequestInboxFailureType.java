package com.slack.bot.infrastructure.review.box.in;

public enum ReviewRequestInboxFailureType {
    NONE,
    NON_RETRYABLE,
    RETRY_EXHAUSTED
}
