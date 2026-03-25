package com.slack.bot.application.interaction.box.in;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import java.time.Duration;
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
class SlackBlockActionInboxWorkerTest {

    @Mock
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    SlackBlockActionInboxWorker slackBlockActionInboxWorker;

    @BeforeEach
    void setUp() {
        slackBlockActionInboxWorker = new SlackBlockActionInboxWorker(
                slackInteractionInboxProcessor,
                1_000L,
                30_000L,
                false
        );
    }

    @Test
    void 워커는_기본_배치_크기로_block_actions_인박스를_처리한다() {
        // when
        slackBlockActionInboxWorker.processBlockActionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingBlockActions(30);
    }

    @Test
    void 기본_생성자는_auto_startup을_활성화한다() {
        // when
        SlackBlockActionInboxWorker worker = new SlackBlockActionInboxWorker(
                slackInteractionInboxProcessor,
                1_000L,
                30_000L
        );

        // then
        assertThat(worker.isAutoStartup()).isTrue();
    }

    @Test
    void 매칭된_wake_up_hint만_poll을_재개한다() {
        // given
        AtomicInteger invocationCount = new AtomicInteger();
        doAnswer(invocation -> {
            invocationCount.incrementAndGet();
            return 0;
        }).when(slackInteractionInboxProcessor).processPendingBlockActions(30);
        slackBlockActionInboxWorker = new SlackBlockActionInboxWorker(
                slackInteractionInboxProcessor,
                30_000L,
                30_000L,
                false
        );

        try {
            slackBlockActionInboxWorker.start();
            await().atMost(Duration.ofSeconds(1L)).until(() -> invocationCount.get() >= 1);

            // when
            slackBlockActionInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.VIEW_SUBMISSION_INBOX));

            // then
            await().during(Duration.ofMillis(300L))
                    .atMost(Duration.ofMillis(500L))
                    .until(() -> invocationCount.get() == 1);

            // when
            slackBlockActionInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.BLOCK_ACTION_INBOX));

            // then
            await().atMost(Duration.ofSeconds(1L)).until(() -> invocationCount.get() >= 2);
        } finally {
            slackBlockActionInboxWorker.stop();
        }
    }

    @Test
    void start와_stop은_running_상태를_변경한다() {
        // when
        slackBlockActionInboxWorker.start();
        boolean runningAfterStart = slackBlockActionInboxWorker.isRunning();
        slackBlockActionInboxWorker.stop();

        // then
        assertAll(
                () -> assertThat(runningAfterStart).isTrue(),
                () -> assertThat(slackBlockActionInboxWorker.isRunning()).isFalse()
        );
    }

    @Test
    void stop_callback은_callback을_실행한다() {
        // given
        AtomicInteger callbackCount = new AtomicInteger();

        // when
        slackBlockActionInboxWorker.stop(() -> callbackCount.incrementAndGet());

        // then
        assertThat(callbackCount.get()).isEqualTo(1);
    }
}
