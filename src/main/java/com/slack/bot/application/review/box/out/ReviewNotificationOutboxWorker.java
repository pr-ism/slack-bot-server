package com.slack.bot.application.review.box.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class ReviewNotificationOutboxWorker {
    private final ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;

    @Value("${review.notification.outbox.batch-size:50}")
    private int batchSize;

    @Value("${review.notification.outbox.processing-timeout-ms:60000}")
    private long processingTimeoutMs;

    @Scheduled(fixedDelayString = "${review.notification.outbox.poll-delay-ms:1000}")
    public void processPendingReviewNotificationOutbox() {
        try {
            reviewNotificationOutboxProcessor.processPending(batchSize, processingTimeoutMs);
        } catch (Exception exception) {
            log.error("review_notification outbox worker 실행에 실패했습니다.", exception);
        }
    }
}
