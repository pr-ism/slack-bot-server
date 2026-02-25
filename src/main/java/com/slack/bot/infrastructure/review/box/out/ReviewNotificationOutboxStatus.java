package com.slack.bot.infrastructure.review.box.out;

public enum ReviewNotificationOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY_PENDING,
    SENT,
    FAILED
}
