package com.slack.bot.application.review.box.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRequestInboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 30;
    private static final long DEFAULT_PROCESSING_TIMEOUT_MS = 60_000L;

    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    @Value("${review.notification.inbox.worker-enabled:true}")
    private boolean workerEnabled;

    @Value("${review.notification.inbox.batch-size:" + DEFAULT_BATCH_SIZE + "}")
    private int batchSize;

    @Value("${review.notification.inbox.processing-timeout-ms:" + DEFAULT_PROCESSING_TIMEOUT_MS + "}")
    private long processingTimeoutMs;

    @Scheduled(fixedDelayString = "${review.notification.inbox.poll-delay-ms:200}")
    public void processPendingReviewRequestInbox() {
        if (!workerEnabled) {
            return;
        }

        try {
            reviewRequestInboxProcessor.processPending(batchSize, processingTimeoutMs);
        } catch (Exception e) {
            log.error("review_request inbox worker 실행에 실패했습니다.", e);
        }
    }
}
