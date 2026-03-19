package com.slack.bot.application.review.box.out;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class ReviewNotificationOutboxWorker {

    private final ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;
    private final int batchSize;

    public ReviewNotificationOutboxWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 0보다 커야 합니다.");
        }
        this.reviewNotificationOutboxProcessor = reviewNotificationOutboxProcessor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${review.notification.outbox.poll-delay-ms:1000}")
    public void processPendingReviewNotificationOutbox() {
        try {
            reviewNotificationOutboxProcessor.processPending(batchSize);
        } catch (Exception exception) {
            log.error("review_notification outbox worker 실행에 실패했습니다.", exception);
        }
    }
}
