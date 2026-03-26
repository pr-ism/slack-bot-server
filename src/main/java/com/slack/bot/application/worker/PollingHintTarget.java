package com.slack.bot.application.worker;

public enum PollingHintTarget {
    BLOCK_ACTION_INBOX,
    VIEW_SUBMISSION_INBOX,
    INTERACTION_OUTBOX,
    REVIEW_REQUEST_INBOX,
    REVIEW_NOTIFICATION_OUTBOX
}
