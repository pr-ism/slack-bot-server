package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class ReviewRequestInboxWorkerTest {

    @Mock
    ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    ReviewRequestInboxWorker reviewRequestInboxWorker;

    @BeforeEach
    void setUp() {
        reviewRequestInboxWorker = new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                30,
                1_000L,
                30_000L,
                false
        );
    }

    @Test
    void worker는_pending을_처리한다() {
        // when
        reviewRequestInboxWorker.processPendingReviewRequestInbox();

        // then
        verify(reviewRequestInboxProcessor).processPending(30);
    }

    @Test
    void 기본_생성자는_auto_startup을_활성화한다() {
        // when
        ReviewRequestInboxWorker worker = new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                30,
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
        ReviewRequestInboxWorker worker = new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                30,
                adaptivePollingRunner
        );

        // when
        worker.wakeUp(new PollingHintEvent(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX));

        // then
        verifyNoInteractions(adaptivePollingRunner);

        // when
        worker.wakeUp(new PollingHintEvent(PollingHintTarget.REVIEW_REQUEST_INBOX));

        // then
        verify(adaptivePollingRunner).wakeUp();
    }

    @Test
    void start와_stop은_running_상태를_변경한다() {
        // when
        reviewRequestInboxWorker.start();
        boolean runningAfterStart = reviewRequestInboxWorker.isRunning();
        reviewRequestInboxWorker.stop();

        // then
        assertAll(
                () -> assertThat(runningAfterStart).isTrue(),
                () -> assertThat(reviewRequestInboxWorker.isRunning()).isFalse()
        );
    }

    @Test
    void stop_callback은_callback을_실행한다() {
        // given
        AtomicInteger callbackCount = new AtomicInteger();

        // when
        reviewRequestInboxWorker.stop(() -> callbackCount.incrementAndGet());

        // then
        assertThat(callbackCount.get()).isEqualTo(1);
    }

    @Test
    void start후_stop_callback은_running을_false로_내리고_callback을_1회_실행한다() {
        // given
        AtomicInteger callbackCount = new AtomicInteger();

        // when
        reviewRequestInboxWorker.start();
        reviewRequestInboxWorker.stop(() -> callbackCount.incrementAndGet());

        // then
        assertAll(
                () -> assertThat(reviewRequestInboxWorker.isRunning()).isFalse(),
                () -> assertThat(callbackCount.get()).isEqualTo(1)
        );
    }

    @Test
    void batchSize가_0이하면_생성자에서_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 0, 1_000L, 30_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize는 0보다 커야 합니다.");
    }

    @Test
    void pollDelayMs가_0이면_생성자에서_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 30, 0L, 30_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pollDelayMs는 0보다 커야 합니다.");
    }

    @Test
    void pollCapMs가_음수면_생성자에서_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 30, 1_000L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pollCapMs는 0보다 커야 합니다.");
    }

    @Test
    void pollCapMs가_pollDelayMs보다_작으면_생성자에서_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 30, 1_000L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pollCapMs는 pollDelayMs 이상이어야 합니다.");
    }
}
