package com.slack.bot.application.review.box.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class ReviewNotificationOutboxTimeoutRecoveryWorker {

    private static final long DEFAULT_PROCESSING_TIMEOUT_MS = 60_000L;

    private final ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;

    @Value("${review.notification.outbox.processing-timeout-ms:" + DEFAULT_PROCESSING_TIMEOUT_MS + "}")
    private long processingTimeoutMs;

    @Scheduled(fixedDelayString = "${review.notification.outbox.poll-delay-ms:1000}")
    public void recoverTimeoutReviewNotificationOutbox() {
        try {
            reviewNotificationOutboxProcessor.recoverTimeoutProcessing(processingTimeoutMs);
        } catch (Exception exception) {
            log.error("review_notification outbox timeout recovery worker 실행에 실패했습니다.", exception);
        }
    }
}
