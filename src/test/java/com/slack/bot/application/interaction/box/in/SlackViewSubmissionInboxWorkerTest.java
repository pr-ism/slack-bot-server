package com.slack.bot.application.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

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
class SlackViewSubmissionInboxWorkerTest {

    @Mock
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    SlackViewSubmissionInboxWorker slackViewSubmissionInboxWorker;

    @BeforeEach
    void setUp() {
        slackViewSubmissionInboxWorker = new SlackViewSubmissionInboxWorker(slackInteractionInboxProcessor);
    }

    @Test
    void 워커는_기본_배치_크기로_view_submission_인박스를_처리한다() {
        // when
        slackViewSubmissionInboxWorker.processViewSubmissionInbox();

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
        assertThatCode(() -> slackViewSubmissionInboxWorker.processViewSubmissionInbox())
                .doesNotThrowAnyException();
    }
}
