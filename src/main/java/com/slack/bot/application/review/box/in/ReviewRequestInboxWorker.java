package com.slack.bot.application.review.box.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class ReviewRequestInboxWorker {
    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    @Value("${review.notification.inbox.batch-size:30}")
    private int batchSize;

    @Value("${review.notification.inbox.processing-timeout-ms:60000}")
    private long processingTimeoutMs;

    @Scheduled(fixedDelayString = "${review.notification.inbox.poll-delay-ms:1000}")
    public void processPendingReviewRequestInbox() {
        try {
            reviewRequestInboxProcessor.processPending(batchSize, processingTimeoutMs);
        } catch (Exception e) {
            log.error("review_request inbox worker 실행에 실패했습니다.", e);
        }
    }
}
