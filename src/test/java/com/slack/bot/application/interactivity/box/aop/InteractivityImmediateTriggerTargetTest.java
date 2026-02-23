package com.slack.bot.application.interactivity.box.aop;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.box.InteractionImmediateProcessor;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractivityImmediateTriggerTargetTest {

    @Test
    void BLOCK_ACTION_INBOX_타겟은_블록액션_인박스를_즉시_처리한다() {
        // given
        InteractionImmediateProcessor interactionImmediateProcessor = mock(InteractionImmediateProcessor.class);

        // when
        InteractivityImmediateTriggerTarget.BLOCK_ACTION_INBOX.trigger(interactionImmediateProcessor);

        // then
        verify(interactionImmediateProcessor).triggerBlockActionInbox();
        verify(interactionImmediateProcessor, never()).triggerViewSubmissionInbox();
        verify(interactionImmediateProcessor, never()).triggerOutbox();
    }

    @Test
    void VIEW_SUBMISSION_INBOX_타겟은_뷰서브미션_인박스를_즉시_처리한다() {
        // given
        InteractionImmediateProcessor interactionImmediateProcessor = mock(InteractionImmediateProcessor.class);

        // when
        InteractivityImmediateTriggerTarget.VIEW_SUBMISSION_INBOX.trigger(interactionImmediateProcessor);

        // then
        verify(interactionImmediateProcessor).triggerViewSubmissionInbox();
        verify(interactionImmediateProcessor, never()).triggerBlockActionInbox();
        verify(interactionImmediateProcessor, never()).triggerOutbox();
    }

    @Test
    void OUTBOX_타겟은_아웃박스를_즉시_처리한다() {
        // given
        InteractionImmediateProcessor interactionImmediateProcessor = mock(InteractionImmediateProcessor.class);

        // when
        InteractivityImmediateTriggerTarget.OUTBOX.trigger(interactionImmediateProcessor);

        // then
        verify(interactionImmediateProcessor).triggerOutbox();
        verify(interactionImmediateProcessor, never()).triggerBlockActionInbox();
        verify(interactionImmediateProcessor, never()).triggerViewSubmissionInbox();
    }
}
