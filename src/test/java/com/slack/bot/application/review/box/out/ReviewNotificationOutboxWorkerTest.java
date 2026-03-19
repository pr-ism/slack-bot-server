package com.slack.bot.application.review.box.out;

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
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxWorkerTest {

    @Mock
    ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;

    ReviewNotificationOutboxWorker reviewNotificationOutboxWorker;

    @BeforeEach
    void setUp() {
        reviewNotificationOutboxWorker = new ReviewNotificationOutboxWorker(reviewNotificationOutboxProcessor);
        ReflectionTestUtils.setField(reviewNotificationOutboxWorker, "batchSize", 50);
        ReflectionTestUtils.setField(reviewNotificationOutboxWorker, "processingTimeoutMs", 60_000L);
    }

    @Test
    void worker는_outbox를_처리한다() {
        // when
        reviewNotificationOutboxWorker.processPendingReviewNotificationOutbox();

        // then
        verify(reviewNotificationOutboxProcessor).processPending(50, 60_000L);
    }

    @Test
    void worker_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(reviewNotificationOutboxProcessor)
                .processPending(50, 60_000L);

        // when & then
        assertThatCode(() -> reviewNotificationOutboxWorker.processPendingReviewNotificationOutbox())
                .doesNotThrowAnyException();
    }
}
