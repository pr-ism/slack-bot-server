package com.slack.bot.application.review.box.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class ReviewNotificationOutboxTimeoutRecoveryWorker {

    private final ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;
    private final long processingTimeoutMs;

    public ReviewNotificationOutboxTimeoutRecoveryWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            long processingTimeoutMs
    ) {
        if (processingTimeoutMs <= 0) {
            throw new IllegalArgumentException("processingTimeoutMs는 0보다 커야 합니다.");
        }
        this.reviewNotificationOutboxProcessor = reviewNotificationOutboxProcessor;
        this.processingTimeoutMs = processingTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${review.notification.outbox.poll-delay-ms:1000}")
    public void recoverTimeoutReviewNotificationOutbox() {
        try {
            reviewNotificationOutboxProcessor.recoverTimeoutProcessing(processingTimeoutMs);
        } catch (Exception exception) {
            log.error("review_notification outbox timeout recovery worker 실행에 실패했습니다.", exception);
        }
    }
}
