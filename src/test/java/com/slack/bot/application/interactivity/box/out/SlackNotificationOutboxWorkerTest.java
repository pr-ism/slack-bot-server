package com.slack.bot.application.interactivity.box.out;

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
class SlackNotificationOutboxWorkerTest {

    @Mock
    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    InteractionWorkerProperties interactionWorkerProperties;

    SlackNotificationOutboxWorker slackNotificationOutboxWorker;

    @BeforeEach
    void setUp() {
        interactionWorkerProperties = new InteractionWorkerProperties();
        slackNotificationOutboxWorker = new SlackNotificationOutboxWorker(
                interactionWorkerProperties,
                slackNotificationOutboxProcessor
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
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(slackNotificationOutboxProcessor)
                .processPending(50);

        // when & then
        assertThatCode(() -> slackNotificationOutboxWorker.processPendingOutbox())
                .doesNotThrowAnyException();
    }

    @Test
    void worker_비활성화_상태라면_outbox를_처리하지_않는다() {
        // given
        interactionWorkerProperties = new InteractionWorkerProperties(
                new InteractionWorkerProperties.Inbox(),
                new InteractionWorkerProperties.Outbox(false, 200L)
        );
        slackNotificationOutboxWorker = new SlackNotificationOutboxWorker(
                interactionWorkerProperties,
                slackNotificationOutboxProcessor
        );

        // when
        slackNotificationOutboxWorker.processPendingOutbox();

        // then
        verify(slackNotificationOutboxProcessor, never()).processPending(50);
    }
}
