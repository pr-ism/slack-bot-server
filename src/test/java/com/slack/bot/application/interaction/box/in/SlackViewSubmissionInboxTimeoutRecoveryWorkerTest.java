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
class SlackViewSubmissionInboxTimeoutRecoveryWorkerTest {

    @Mock
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    SlackViewSubmissionInboxTimeoutRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new SlackViewSubmissionInboxTimeoutRecoveryWorker(slackInteractionInboxProcessor);
    }

    @Test
    void timeout_recovery를_실행한다() {
        // when
        worker.recoverTimeoutViewSubmissionInbox();

        // then
        verify(slackInteractionInboxProcessor).recoverViewSubmissionTimeoutProcessing();
    }

    @Test
    void timeout_recovery_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(slackInteractionInboxProcessor)
                .recoverViewSubmissionTimeoutProcessing();

        // when & then
        assertThatCode(() -> worker.recoverTimeoutViewSubmissionInbox()).doesNotThrowAnyException();
    }
}
