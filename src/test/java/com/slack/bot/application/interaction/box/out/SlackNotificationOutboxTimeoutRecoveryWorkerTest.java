package com.slack.bot.application.interaction.box.out;

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
class SlackNotificationOutboxTimeoutRecoveryWorkerTest {

    @Mock
    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    SlackNotificationOutboxTimeoutRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new SlackNotificationOutboxTimeoutRecoveryWorker(slackNotificationOutboxProcessor);
    }

    @Test
    void timeout_recovery를_실행한다() {
        // when
        worker.recoverTimeoutOutbox();

        // then
        verify(slackNotificationOutboxProcessor).recoverTimeoutProcessing();
    }

    @Test
    void timeout_recovery_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(slackNotificationOutboxProcessor)
                .recoverTimeoutProcessing();

        // when & then
        assertThatCode(() -> worker.recoverTimeoutOutbox()).doesNotThrowAnyException();
    }
}
