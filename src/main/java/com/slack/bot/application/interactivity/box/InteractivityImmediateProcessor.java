package com.slack.bot.application.interactivity.box;

import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractivityImmediateProcessor {

    private static final int IMMEDIATE_BATCH_SIZE = 1;

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;
    private final SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    public void triggerBlockActionInbox() {
        runAfterCommit(
                "block_actions 인박스 즉시 처리",
                () -> slackInteractionInboxProcessor.processPendingBlockActions(IMMEDIATE_BATCH_SIZE)
        );
    }

    public void triggerViewSubmissionInbox() {
        runAfterCommit(
                "view_submission 인박스 즉시 처리",
                () -> slackInteractionInboxProcessor.processPendingViewSubmissions(IMMEDIATE_BATCH_SIZE)
        );
    }

    public void triggerOutbox() {
        runAfterCommit(
                "아웃박스 즉시 처리",
                () -> slackNotificationOutboxProcessor.processPending(IMMEDIATE_BATCH_SIZE)
        );
    }

    private void runAfterCommit(String taskName, Runnable runnable) {
        if (!isTransactionActive()) {
            runSafely(taskName, runnable);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(taskName, runnable);
            }
        });
    }

    private boolean isTransactionActive() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    private void runSafely(String taskName, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("{}에 실패했습니다. 스케줄러 복구에 위임합니다.", taskName, e);
        }
    }
}
