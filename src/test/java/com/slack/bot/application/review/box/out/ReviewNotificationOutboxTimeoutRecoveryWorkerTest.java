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
class ReviewNotificationOutboxTimeoutRecoveryWorkerTest {

    @Mock
    ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor;

    ReviewNotificationOutboxTimeoutRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ReviewNotificationOutboxTimeoutRecoveryWorker(reviewNotificationOutboxProcessor);
        ReflectionTestUtils.setField(worker, "processingTimeoutMs", 60_000L);
    }

    @Test
    void timeout_recovery를_실행한다() {
        // when
        worker.recoverTimeoutReviewNotificationOutbox();

        // then
        verify(reviewNotificationOutboxProcessor).recoverTimeoutProcessing(60_000L);
    }

    @Test
    void timeout_recovery_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(reviewNotificationOutboxProcessor)
                .recoverTimeoutProcessing(60_000L);

        // when & then
        assertThatCode(() -> worker.recoverTimeoutReviewNotificationOutbox()).doesNotThrowAnyException();
    }
}
