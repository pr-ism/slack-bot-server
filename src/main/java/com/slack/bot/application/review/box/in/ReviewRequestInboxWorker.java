package com.slack.bot.application.review.box.in;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class ReviewRequestInboxWorker {

    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;
    private final int batchSize;

    public ReviewRequestInboxWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 0보다 커야 합니다.");
        }
        this.reviewRequestInboxProcessor = reviewRequestInboxProcessor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${review.notification.inbox.poll-delay-ms:1000}")
    public void processPendingReviewRequestInbox() {
        try {
            reviewRequestInboxProcessor.processPending(batchSize);
        } catch (Exception e) {
            log.error("review_request inbox worker 실행에 실패했습니다.", e);
        }
    }
}
