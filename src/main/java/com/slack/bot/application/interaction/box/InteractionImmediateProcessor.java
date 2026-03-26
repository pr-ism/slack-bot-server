package com.slack.bot.application.interaction.box;

import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionImmediateProcessor {
    private final TaskExecutor reviewInteractionExecutor;
    private final PollingHintPublisher pollingHintPublisher;

    public void triggerBlockActionInbox() {
        runAfterCommit(
                "block_actions 인박스 wake-up",
                () -> pollingHintPublisher.publish(PollingHintTarget.BLOCK_ACTION_INBOX)
        );
    }

    public void triggerViewSubmissionInbox() {
        runAfterCommit(
                "view_submission 인박스 wake-up",
                () -> pollingHintPublisher.publish(PollingHintTarget.VIEW_SUBMISSION_INBOX)
        );
    }

    public void triggerOutbox() {
        runAfterCommit(
                "interaction outbox wake-up",
                () -> pollingHintPublisher.publish(PollingHintTarget.INTERACTION_OUTBOX)
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
                runAsync(taskName, runnable);
            }
        });
    }

    private boolean isTransactionActive() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    private void runAsync(String taskName, Runnable runnable) {
        try {
            reviewInteractionExecutor.execute(() -> runSafely(taskName, runnable));
        } catch (Exception e) {
            log.error("{} 작업을 비동기로 제출하지 못했습니다. 스케줄러 복구에 위임합니다.", taskName, e);
        }
    }

    private void runSafely(String taskName, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("{}에 실패했습니다. 스케줄러 복구에 위임합니다.", taskName, e);
        }
    }
}
