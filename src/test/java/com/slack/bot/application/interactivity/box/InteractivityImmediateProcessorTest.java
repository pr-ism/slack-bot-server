package com.slack.bot.application.interactivity.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxProcessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractivityImmediateProcessorTest {

    private InteractivityImmediateProcessor interactivityImmediateProcessor;
    private SlackInteractionInboxProcessor slackInteractionInboxProcessor;
    private SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @BeforeEach
    void setUp() {
        slackInteractionInboxProcessor = mock(SlackInteractionInboxProcessor.class);
        slackNotificationOutboxProcessor = mock(SlackNotificationOutboxProcessor.class);
        interactivityImmediateProcessor = new InteractivityImmediateProcessor(
                slackInteractionInboxProcessor,
                slackNotificationOutboxProcessor
        );

        TransactionSynchronizationManager.clear();
    }

    @Test
    void 트랜잭션이_없을때는_즉시_실행된다() {
        // when
        interactivityImmediateProcessor.triggerBlockActionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingBlockActions(anyInt());
    }

    @Test
    void 트랜잭션이_활성화된_경우_커밋_후에_실행된다() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // when
        interactivityImmediateProcessor.triggerBlockActionInbox();

        // then
        verify(slackInteractionInboxProcessor, never()).processPendingBlockActions(anyInt());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();

        assertThat(synchronizations).hasSize(1);

        synchronizations.getFirst().afterCommit();

        verify(slackInteractionInboxProcessor).processPendingBlockActions(anyInt());
    }

    @Test
    void view_submission_인박스_트리거시_프로세서가_호출된다() {
        // when
        interactivityImmediateProcessor.triggerViewSubmissionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingViewSubmissions(anyInt());
    }

    @Test
    void outbox_트리거시_프로세서가_호출된다() {
        // when
        interactivityImmediateProcessor.triggerOutbox();

        // then
        verify(slackNotificationOutboxProcessor).processPending(anyInt());
    }

    @Test
    void 프로세서_실행중_예외가_발생해도_전파되지_않는다() {
        // given
        willThrow(new RuntimeException("error"))
                .given(slackInteractionInboxProcessor)
                .processPendingBlockActions(anyInt());

        // when
        interactivityImmediateProcessor.triggerBlockActionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingBlockActions(anyInt());
    }
}
