package com.slack.bot.application.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.slack.bot.application.worker.AdaptivePollingRunner;
import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import java.lang.reflect.Field;
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
        AdaptivePollingRunner adaptivePollingRunner = mock(AdaptivePollingRunner.class);
        replaceAdaptivePollingRunner(adaptivePollingRunner);

        // when
        slackBlockActionInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.VIEW_SUBMISSION_INBOX));

        // then
        verifyNoInteractions(adaptivePollingRunner);

        // when
        slackBlockActionInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.BLOCK_ACTION_INBOX));

        // then
        verify(adaptivePollingRunner).wakeUp();
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

    private void replaceAdaptivePollingRunner(AdaptivePollingRunner adaptivePollingRunner) {
        try {
            Field field = SlackBlockActionInboxWorker.class.getDeclaredField("adaptivePollingRunner");
            field.setAccessible(true);
            field.set(slackBlockActionInboxWorker, adaptivePollingRunner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("adaptivePollingRunner 교체에 실패했습니다.", e);
        }
    }
}
