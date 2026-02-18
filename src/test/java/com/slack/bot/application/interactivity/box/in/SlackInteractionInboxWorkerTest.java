package com.slack.bot.application.interactivity.box.in;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxWorkerTest {

    @Mock
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    InteractionWorkerProperties interactionWorkerProperties;

    SlackInteractionInboxWorker slackInteractionInboxWorker;

    @BeforeEach
    void setUp() {
        interactionWorkerProperties = new InteractionWorkerProperties();
        slackInteractionInboxWorker = new SlackInteractionInboxWorker(
                interactionWorkerProperties,
                slackInteractionInboxProcessor
        );
    }

    @Test
    void 워커는_기본_배치_크기로_block_actions_인박스를_처리한다() {
        // when
        slackInteractionInboxWorker.processBlockActionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingBlockActions(30);
    }

    @Test
    void 워커_실행_중_예외가_발생해도_예외를_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(slackInteractionInboxProcessor)
                .processPendingBlockActions(30);

        // when & then
        assertThatCode(() -> slackInteractionInboxWorker.processBlockActionInbox())
                .doesNotThrowAnyException();
    }

    @Test
    void 워커는_기본_배치_크기로_view_submission_인박스를_처리한다() {
        // when
        slackInteractionInboxWorker.processViewSubmissionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingViewSubmissions(30);
    }

    @Test
    void view_submission_워커_실행_중_예외가_발생해도_예외를_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(slackInteractionInboxProcessor)
                .processPendingViewSubmissions(30);

        // when & then
        assertThatCode(() -> slackInteractionInboxWorker.processViewSubmissionInbox())
                .doesNotThrowAnyException();
    }

    @Test
    void worker_enabled가_false면_block_actions_인박스를_처리하지_않는다() {
        // given
        interactionWorkerProperties = new InteractionWorkerProperties(
                new InteractionWorkerProperties.Inbox(
                        new InteractionWorkerProperties.BlockActions(false, 200L),
                        new InteractionWorkerProperties.ViewSubmission()
                ),
                new InteractionWorkerProperties.Outbox()
        );
        slackInteractionInboxWorker = new SlackInteractionInboxWorker(
                interactionWorkerProperties,
                slackInteractionInboxProcessor
        );

        // when
        slackInteractionInboxWorker.processBlockActionInbox();

        // then
        verify(slackInteractionInboxProcessor, never()).processPendingBlockActions(30);
    }

    @Test
    void worker_enabled가_false면_view_submission_인박스를_처리하지_않는다() {
        // given
        interactionWorkerProperties = new InteractionWorkerProperties(
                new InteractionWorkerProperties.Inbox(
                        new InteractionWorkerProperties.BlockActions(true, 200L),
                        new InteractionWorkerProperties.ViewSubmission(false, 200L)
                ),
                new InteractionWorkerProperties.Outbox()
        );
        slackInteractionInboxWorker = new SlackInteractionInboxWorker(
                interactionWorkerProperties,
                slackInteractionInboxProcessor
        );

        // when
        slackInteractionInboxWorker.processViewSubmissionInbox();

        // then
        verify(slackInteractionInboxProcessor, never()).processPendingViewSubmissions(30);
    }

    @Test
    void block_actions_worker_enabled가_false여도_view_submission_worker_enabled가_true면_view_submission은_처리한다() {
        // given
        interactionWorkerProperties = new InteractionWorkerProperties(
                new InteractionWorkerProperties.Inbox(
                        new InteractionWorkerProperties.BlockActions(false, 200L),
                        new InteractionWorkerProperties.ViewSubmission(true, 200L)
                ),
                new InteractionWorkerProperties.Outbox()
        );
        slackInteractionInboxWorker = new SlackInteractionInboxWorker(
                interactionWorkerProperties,
                slackInteractionInboxProcessor
        );

        // when
        slackInteractionInboxWorker.processViewSubmissionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingViewSubmissions(30);
    }
}
