package com.slack.bot.application.review.box.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewNotificationOutboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long DEFAULT_PROCESSING_TIMEOUT_MS = 60_000L;

    private final ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;

    @Value("${review.notification.outbox.worker-enabled:true}")
    private boolean workerEnabled;

    @Value("${review.notification.outbox.batch-size:" + DEFAULT_BATCH_SIZE + "}")
    private int batchSize;

    @Value("${review.notification.outbox.processing-timeout-ms:" + DEFAULT_PROCESSING_TIMEOUT_MS + "}")
    private long processingTimeoutMs;

    @Scheduled(fixedDelayString = "${review.notification.outbox.poll-delay-ms:200}")
    public void processPendingReviewNotificationOutbox() {
        if (!workerEnabled) {
            return;
        }

        try {
            reviewNotificationOutboxProcessor.processPending(batchSize, processingTimeoutMs);
        } catch (Exception exception) {
            log.error("review_notification outbox worker 실행에 실패했습니다.", exception);
        }
    }
}
