package com.slack.bot.application.review.box.in;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        AtomicInteger invocationCount = new AtomicInteger();
        doAnswer(invocation -> {
            invocationCount.incrementAndGet();
            return 0;
        }).when(reviewRequestInboxProcessor).processPending(30);
        reviewRequestInboxWorker = new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                30,
                30_000L,
                30_000L,
                false
        );

        try {
            reviewRequestInboxWorker.start();
            await().atMost(Duration.ofSeconds(1L)).until(() -> invocationCount.get() >= 1);

            // when
            reviewRequestInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX));

            // then
            await().during(Duration.ofMillis(300L))
                    .atMost(Duration.ofMillis(500L))
                    .until(() -> invocationCount.get() == 1);

            // when
            reviewRequestInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.REVIEW_REQUEST_INBOX));

            // then
            await().atMost(Duration.ofSeconds(1L)).until(() -> invocationCount.get() >= 2);
        } finally {
            reviewRequestInboxWorker.stop();
        }
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
    void batchSize가_0이하면_생성자에서_예외가_발생한다() {
        assertThatThrownBy(() -> new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 0, 1_000L, 30_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize는 0보다 커야 합니다.");
    }
}
