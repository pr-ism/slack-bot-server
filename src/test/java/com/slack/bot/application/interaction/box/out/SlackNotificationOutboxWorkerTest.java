package com.slack.bot.application.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.slack.bot.application.worker.AdaptivePollingRunner;
import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import java.util.concurrent.atomic.AtomicInteger;
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
                30_000L,
                false
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
    void 기본_생성자는_auto_startup을_활성화한다() {
        // when
        SlackNotificationOutboxWorker worker = new SlackNotificationOutboxWorker(
                slackNotificationOutboxProcessor,
                1_000L,
                30_000L
        );

        // then
        assertThat(worker.isAutoStartup()).isTrue();
    }

    @Test
    void 매칭된_wake_up_hint만_poll을_재개한다() {
        // given
        AdaptivePollingRunner adaptivePollingRunner = mock(AdaptivePollingRunner.class);
        SlackNotificationOutboxWorker worker = new SlackNotificationOutboxWorker(
                slackNotificationOutboxProcessor,
                adaptivePollingRunner
        );

        // when
        worker.wakeUp(new PollingHintEvent(PollingHintTarget.BLOCK_ACTION_INBOX));

        // then
        verifyNoInteractions(adaptivePollingRunner);

        // when
        worker.wakeUp(new PollingHintEvent(PollingHintTarget.INTERACTION_OUTBOX));

        // then
        verify(adaptivePollingRunner).wakeUp();
    }

    @Test
    void start와_stop은_running_상태를_변경한다() {
        // when
        slackNotificationOutboxWorker.start();
        boolean runningAfterStart = slackNotificationOutboxWorker.isRunning();
        slackNotificationOutboxWorker.stop();

        // then
        assertAll(
                () -> assertThat(runningAfterStart).isTrue(),
                () -> assertThat(slackNotificationOutboxWorker.isRunning()).isFalse()
        );
    }

    @Test
    void stop_callback은_callback을_실행한다() {
        // given
        AtomicInteger callbackCount = new AtomicInteger();

        // when
        slackNotificationOutboxWorker.stop(() -> callbackCount.incrementAndGet());

        // then
        assertThat(callbackCount.get()).isEqualTo(1);
    }
}
