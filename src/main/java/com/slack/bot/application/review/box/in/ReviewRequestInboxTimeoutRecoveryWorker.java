package com.slack.bot.application.review.box.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class ReviewRequestInboxTimeoutRecoveryWorker {

    private static final long DEFAULT_PROCESSING_TIMEOUT_MS = 60_000L;

    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    @Value("${review.notification.inbox.processing-timeout-ms:" + DEFAULT_PROCESSING_TIMEOUT_MS + "}")
    private long processingTimeoutMs;

    @Scheduled(fixedDelayString = "${review.notification.inbox.poll-delay-ms:1000}")
    public void recoverTimeoutReviewRequestInbox() {
        try {
            reviewRequestInboxProcessor.recoverTimeoutProcessing(processingTimeoutMs);
        } catch (Exception e) {
            log.error("review_request inbox timeout recovery worker 실행에 실패했습니다.", e);
        }
    }
}
