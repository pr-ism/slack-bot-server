package com.slack.bot.application.review.box.in;

import com.slack.bot.application.worker.AdaptivePollingRunner;
import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import java.time.Duration;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;

public class ReviewRequestInboxWorker implements SmartLifecycle {

    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;
    private final int batchSize;
    private final AdaptivePollingRunner adaptivePollingRunner;

    public ReviewRequestInboxWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            int batchSize,
            long pollDelayMs,
            long pollCapMs
    ) {
        this(reviewRequestInboxProcessor, batchSize, pollDelayMs, pollCapMs, true);
    }

    public ReviewRequestInboxWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            int batchSize,
            long pollDelayMs,
            long pollCapMs,
            boolean autoStartup
    ) {
        this(
                reviewRequestInboxProcessor,
                batchSize,
                new AdaptivePollingRunner(
                        "review_request inbox worker",
                        Duration.ofMillis(pollDelayMs),
                        Duration.ofMillis(pollCapMs),
                        () -> reviewRequestInboxProcessor.processPending(batchSize),
                        autoStartup
                )
        );
    }

    ReviewRequestInboxWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            int batchSize,
            AdaptivePollingRunner adaptivePollingRunner
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 0보다 커야 합니다.");
        }
        this.reviewRequestInboxProcessor = reviewRequestInboxProcessor;
        this.batchSize = batchSize;
        this.adaptivePollingRunner = adaptivePollingRunner;
    }

    public int processPendingReviewRequestInbox() {
        return reviewRequestInboxProcessor.processPending(batchSize);
    }

    @EventListener
    public void wakeUp(PollingHintEvent pollingHintEvent) {
        if (pollingHintEvent.target() == PollingHintTarget.REVIEW_REQUEST_INBOX) {
            adaptivePollingRunner.wakeUp();
        }
    }

    @Override
    public void start() {
        adaptivePollingRunner.start();
    }

    @Override
    public void stop() {
        adaptivePollingRunner.stop();
    }

    @Override
    public void stop(Runnable callback) {
        adaptivePollingRunner.stop(callback);
    }

    @Override
    public boolean isRunning() {
        return adaptivePollingRunner.isRunning();
    }

    @Override
    public boolean isAutoStartup() {
        return adaptivePollingRunner.isAutoStartup();
    }

    @Override
    public int getPhase() {
        return adaptivePollingRunner.getPhase();
    }
}
