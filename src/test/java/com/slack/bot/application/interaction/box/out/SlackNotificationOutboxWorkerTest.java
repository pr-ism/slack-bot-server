package com.slack.bot.application.interaction.box.out;

import static org.mockito.Mockito.verify;

import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
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

    SlackNotificationOutboxWorker slackNotificationOutboxWorker;

    @BeforeEach
    void setUp() {
        slackNotificationOutboxWorker = new SlackNotificationOutboxWorker(
                slackNotificationOutboxProcessor,
                1_000L,
                30_000L
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
    void wake_up_hint와_stop은_예외없이_동작한다() {
        slackNotificationOutboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.INTERACTION_OUTBOX));
        slackNotificationOutboxWorker.stop();
    }
}
