package com.slack.bot.infrastructure.review.box.in;

public enum ReviewRequestInboxStatus {
    PENDING,
    PROCESSING,
    RETRY_PENDING,
    PROCESSED,
    FAILED
}
