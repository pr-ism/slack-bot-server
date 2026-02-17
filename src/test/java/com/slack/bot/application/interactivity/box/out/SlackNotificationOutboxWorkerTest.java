package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.global.config.properties.InteractivityWorkerProperties;
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
class SlackNotificationOutboxWorkerTest {

    @Mock
    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    InteractivityWorkerProperties interactivityWorkerProperties;

    SlackNotificationOutboxWorker slackNotificationOutboxWorker;

    @BeforeEach
    void setUp() {
        interactivityWorkerProperties = new InteractivityWorkerProperties();
        slackNotificationOutboxWorker = new SlackNotificationOutboxWorker(
                slackNotificationOutboxProcessor,
                interactivityWorkerProperties
        );
    }

    @Test
    void 워커는_기본_배치_크기로_outbox를_처리한다() {
        // when
        slackNotificationOutboxWorker.processPendingOutbox();

        // then
        verify(slackNotificationOutboxProcessor).processPending(50);
    }

    @Test
    void 워커_실행_중_예외가_발생해도_예외를_전파하지_않는다() {
        // when
        willThrow(new RuntimeException("worker failure"))
                .given(slackNotificationOutboxProcessor)
                .processPending(50);

        // then
        assertThatCode(() -> slackNotificationOutboxWorker.processPendingOutbox())
                .doesNotThrowAnyException();
    }

    @Test
    void worker_enabled가_false면_outbox를_처리하지_않는다() {
        // given
        interactivityWorkerProperties = new InteractivityWorkerProperties(
                new InteractivityWorkerProperties.Inbox(),
                new InteractivityWorkerProperties.Outbox(false, 200L)
        );
        slackNotificationOutboxWorker = new SlackNotificationOutboxWorker(
                slackNotificationOutboxProcessor,
                interactivityWorkerProperties
        );

        // when
        slackNotificationOutboxWorker.processPendingOutbox();

        // then
        verify(slackNotificationOutboxProcessor, never()).processPending(50);
    }
}
