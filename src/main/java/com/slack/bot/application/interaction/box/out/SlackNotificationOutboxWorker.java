package com.slack.bot.application.interaction.box.out;

import com.slack.bot.application.worker.AdaptivePollingRunner;
import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import java.time.Duration;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;

public class SlackNotificationOutboxWorker implements SmartLifecycle {

    private static final int BATCH_SIZE = 50;

    private final SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;
    private final AdaptivePollingRunner adaptivePollingRunner;

    public SlackNotificationOutboxWorker(
            SlackNotificationOutboxProcessor slackNotificationOutboxProcessor,
            long pollDelayMs,
            long pollCapMs
    ) {
        this(slackNotificationOutboxProcessor, pollDelayMs, pollCapMs, true);
    }

    public SlackNotificationOutboxWorker(
            SlackNotificationOutboxProcessor slackNotificationOutboxProcessor,
            long pollDelayMs,
            long pollCapMs,
            boolean autoStartup
    ) {
        this(
                slackNotificationOutboxProcessor,
                new AdaptivePollingRunner(
                        "interaction outbox worker",
                        Duration.ofMillis(pollDelayMs),
                        Duration.ofMillis(pollCapMs),
                        () -> slackNotificationOutboxProcessor.processPending(BATCH_SIZE),
                        autoStartup
                )
        );
    }

    SlackNotificationOutboxWorker(
            SlackNotificationOutboxProcessor slackNotificationOutboxProcessor,
            AdaptivePollingRunner adaptivePollingRunner
    ) {
        this.slackNotificationOutboxProcessor = slackNotificationOutboxProcessor;
        this.adaptivePollingRunner = adaptivePollingRunner;
    }

    public int processPendingOutbox() {
        return slackNotificationOutboxProcessor.processPending(BATCH_SIZE);
    }

    @EventListener
    public void wakeUp(PollingHintEvent pollingHintEvent) {
        if (pollingHintEvent.target() == PollingHintTarget.INTERACTION_OUTBOX) {
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
