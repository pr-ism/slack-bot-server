package com.slack.bot.application.interaction.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractionImmediateProcessorTest {

    private InteractionImmediateProcessor interactionImmediateProcessor;
    private PollingHintPublisher pollingHintPublisher;
    private TaskExecutor interactionImmediateExecutor;

    @BeforeEach
    void setUp() {
        pollingHintPublisher = mock(PollingHintPublisher.class);
        interactionImmediateExecutor = runnable -> runnable.run();
        interactionImmediateProcessor = new InteractionImmediateProcessor(
                interactionImmediateExecutor,
                pollingHintPublisher
        );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void 트랜잭션이_없을때는_즉시_실행된다() {
        // when
        interactionImmediateProcessor.triggerBlockActionInbox();

        // then
        verify(pollingHintPublisher).publish(PollingHintTarget.BLOCK_ACTION_INBOX);
    }

    @Test
    void 트랜잭션이_활성화된_경우_커밋_후에_실행된다() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // when
        interactionImmediateProcessor.triggerBlockActionInbox();

        // then
        verify(pollingHintPublisher, never()).publish(PollingHintTarget.BLOCK_ACTION_INBOX);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();

        assertThat(synchronizations).hasSize(1);

        synchronizations.getFirst().afterCommit();

        verify(pollingHintPublisher).publish(PollingHintTarget.BLOCK_ACTION_INBOX);
    }

    @Test
    void view_submission_인박스_트리거시_프로세서가_호출된다() {
        // when
        interactionImmediateProcessor.triggerViewSubmissionInbox();

        // then
        verify(pollingHintPublisher).publish(PollingHintTarget.VIEW_SUBMISSION_INBOX);
    }

    @Test
    void outbox_트리거시_프로세서가_호출된다() {
        // when
        interactionImmediateProcessor.triggerOutbox();

        // then
        verify(pollingHintPublisher).publish(PollingHintTarget.INTERACTION_OUTBOX);
    }

    @Test
    void 프로세서_실행중_예외가_발생해도_전파되지_않는다() {
        // given
        willThrow(new RuntimeException("error"))
                .given(pollingHintPublisher)
                .publish(PollingHintTarget.BLOCK_ACTION_INBOX);

        // when
        interactionImmediateProcessor.triggerBlockActionInbox();

        // then
        verify(pollingHintPublisher).publish(PollingHintTarget.BLOCK_ACTION_INBOX);
    }

    @Test
    void 커밋후_비동기_제출에_실패해도_예외는_전파되지_않는다() {
        // given
        interactionImmediateExecutor = runnable -> {
            throw new RuntimeException("executor submit failed");
        };
        interactionImmediateProcessor = new InteractionImmediateProcessor(
                interactionImmediateExecutor,
                pollingHintPublisher
        );

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // when
        interactionImmediateProcessor.triggerBlockActionInbox();
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.getFirst().afterCommit();

        // then
        verify(pollingHintPublisher, never()).publish(PollingHintTarget.BLOCK_ACTION_INBOX);
    }
}
