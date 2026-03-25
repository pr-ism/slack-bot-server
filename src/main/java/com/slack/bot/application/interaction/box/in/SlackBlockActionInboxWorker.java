package com.slack.bot.application.interaction.box.in;

import com.slack.bot.application.worker.AdaptivePollingRunner;
import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import java.time.Duration;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;

public class SlackBlockActionInboxWorker implements SmartLifecycle {

    private static final int BATCH_SIZE = 30;

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;
    private final AdaptivePollingRunner adaptivePollingRunner;

    public SlackBlockActionInboxWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor,
            long pollDelayMs,
            long pollCapMs
    ) {
        this(slackInteractionInboxProcessor, pollDelayMs, pollCapMs, true);
    }

    public SlackBlockActionInboxWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor,
            long pollDelayMs,
            long pollCapMs,
            boolean autoStartup
    ) {
        this.slackInteractionInboxProcessor = slackInteractionInboxProcessor;
        this.adaptivePollingRunner = new AdaptivePollingRunner(
                "block_actions inbox worker",
                Duration.ofMillis(pollDelayMs),
                Duration.ofMillis(pollCapMs),
                () -> processBlockActionInbox(),
                autoStartup
        );
    }

    public int processBlockActionInbox() {
        return slackInteractionInboxProcessor.processPendingBlockActions(BATCH_SIZE);
    }

    @EventListener
    public void wakeUp(PollingHintEvent pollingHintEvent) {
        if (pollingHintEvent.target() == PollingHintTarget.BLOCK_ACTION_INBOX) {
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
