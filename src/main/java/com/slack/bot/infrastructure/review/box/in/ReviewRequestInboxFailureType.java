package com.slack.bot.infrastructure.review.box.in;

public enum ReviewRequestInboxFailureType {
    NONE,
    PROCESSING_TIMEOUT,
    NON_RETRYABLE,
    RETRY_EXHAUSTED
}
