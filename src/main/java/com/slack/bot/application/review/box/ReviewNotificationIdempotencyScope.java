package com.slack.bot.application.review.box;

import lombok.Getter;

@Getter
public enum ReviewNotificationIdempotencyScope {
    REVIEW_NOTIFICATION_OUTBOX("review_notification_outbox");

    private final String value;

    ReviewNotificationIdempotencyScope(String value) {
        this.value = value;
    }
}
