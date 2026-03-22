package com.slack.bot.application.review.box.in;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class ReviewRequestInboxTimeoutRecoveryWorker {

    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;
    private final long processingTimeoutMs;

    public ReviewRequestInboxTimeoutRecoveryWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            long processingTimeoutMs
    ) {
        if (processingTimeoutMs <= 0) {
            throw new IllegalArgumentException("processingTimeoutMs는 0보다 커야 합니다.");
        }
        this.reviewRequestInboxProcessor = reviewRequestInboxProcessor;
        this.processingTimeoutMs = processingTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${review.notification.inbox.poll-delay-ms:1000}")
    public void recoverTimeoutReviewRequestInbox() {
        try {
            reviewRequestInboxProcessor.recoverTimeoutProcessing(processingTimeoutMs);
        } catch (Exception e) {
            log.error("review_request inbox timeout recovery worker 실행에 실패했습니다.", e);
        }
    }
}
