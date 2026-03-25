package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class ReviewNotificationOutboxWorkerTest {

    @Mock
    ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;

    ReviewNotificationOutboxWorker reviewNotificationOutboxWorker;

    @BeforeEach
    void setUp() {
        reviewNotificationOutboxWorker = new ReviewNotificationOutboxWorker(
                reviewNotificationOutboxProcessor,
                50,
                1_000L,
                30_000L,
                false
        );
    }

    @Test
    void worker는_outbox를_처리한다() {
        // when
        reviewNotificationOutboxWorker.processPendingReviewNotificationOutbox();

        // then
        verify(reviewNotificationOutboxProcessor).processPending(50);
    }

    @Test
    void 기본_생성자는_auto_startup을_활성화한다() {
        // when
        ReviewNotificationOutboxWorker worker = new ReviewNotificationOutboxWorker(
                reviewNotificationOutboxProcessor,
                50,
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
        reviewNotificationOutboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.REVIEW_REQUEST_INBOX));

        // then
        verifyNoInteractions(adaptivePollingRunner);

        // when
        reviewNotificationOutboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX));

        // then
        verify(adaptivePollingRunner).wakeUp();
    }

    @Test
    void start와_stop은_running_상태를_변경한다() {
        // when
        reviewNotificationOutboxWorker.start();
        boolean runningAfterStart = reviewNotificationOutboxWorker.isRunning();
        reviewNotificationOutboxWorker.stop();

        // then
        assertAll(
                () -> assertThat(runningAfterStart).isTrue(),
                () -> assertThat(reviewNotificationOutboxWorker.isRunning()).isFalse()
        );
    }

    @Test
    void stop_callback은_callback을_실행한다() {
        // given
        AtomicInteger callbackCount = new AtomicInteger();

        // when
        reviewNotificationOutboxWorker.stop(() -> callbackCount.incrementAndGet());

        // then
        assertThat(callbackCount.get()).isEqualTo(1);
    }

    @Test
    void batchSize가_0이하면_생성자에서_예외가_발생한다() {
        assertThatThrownBy(() -> new ReviewNotificationOutboxWorker(
                reviewNotificationOutboxProcessor,
                0,
                1_000L,
                30_000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize는 0보다 커야 합니다.");
    }

    private void replaceAdaptivePollingRunner(AdaptivePollingRunner adaptivePollingRunner) {
        try {
            Field field = ReviewNotificationOutboxWorker.class.getDeclaredField("adaptivePollingRunner");
            field.setAccessible(true);
            field.set(reviewNotificationOutboxWorker, adaptivePollingRunner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("adaptivePollingRunner 교체에 실패했습니다.", e);
        }
    }
}
