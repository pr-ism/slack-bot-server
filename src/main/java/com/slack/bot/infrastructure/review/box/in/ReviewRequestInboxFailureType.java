package com.slack.bot.infrastructure.review.box.in;

public enum ReviewRequestInboxFailureType {
    RETRYABLE,
    PROCESSING_TIMEOUT,
    NON_RETRYABLE,
    RETRY_EXHAUSTED
}
