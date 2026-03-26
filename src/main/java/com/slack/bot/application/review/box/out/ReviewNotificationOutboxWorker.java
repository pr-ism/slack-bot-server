package com.slack.bot.application.review.box.out;

import com.slack.bot.application.worker.AdaptivePollingRunner;
import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import java.time.Duration;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;

public class ReviewNotificationOutboxWorker implements SmartLifecycle {

    private final ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;
    private final int batchSize;
    private final AdaptivePollingRunner adaptivePollingRunner;

    public ReviewNotificationOutboxWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            int batchSize,
            long pollDelayMs,
            long pollCapMs
    ) {
        this(reviewNotificationOutboxProcessor, batchSize, pollDelayMs, pollCapMs, true);
    }

    public ReviewNotificationOutboxWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            int batchSize,
            long pollDelayMs,
            long pollCapMs,
            boolean autoStartup
    ) {
        this(
                reviewNotificationOutboxProcessor,
                batchSize,
                new AdaptivePollingRunner(
                        "review_notification outbox worker",
                        Duration.ofMillis(pollDelayMs),
                        Duration.ofMillis(pollCapMs),
                        () -> reviewNotificationOutboxProcessor.processPending(batchSize),
                        autoStartup
                )
        );
    }

    ReviewNotificationOutboxWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            int batchSize,
            AdaptivePollingRunner adaptivePollingRunner
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 0보다 커야 합니다.");
        }
        this.reviewNotificationOutboxProcessor = reviewNotificationOutboxProcessor;
        this.batchSize = batchSize;
        this.adaptivePollingRunner = adaptivePollingRunner;
    }

    public int processPendingReviewNotificationOutbox() {
        return reviewNotificationOutboxProcessor.processPending(batchSize);
    }

    @EventListener
    public void wakeUp(PollingHintEvent pollingHintEvent) {
        if (pollingHintEvent.target() == PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX) {
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
