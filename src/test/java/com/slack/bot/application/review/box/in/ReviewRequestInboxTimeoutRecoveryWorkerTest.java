package com.slack.bot.application.review.box.in;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxTimeoutRecoveryWorkerTest {

    @Mock
    ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    ReviewRequestInboxTimeoutRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ReviewRequestInboxTimeoutRecoveryWorker(reviewRequestInboxProcessor, 60_000L);
    }

    @Test
    void timeout_recovery를_실행한다() {
        // when
        worker.recoverTimeoutReviewRequestInbox();

        // then
        verify(reviewRequestInboxProcessor).recoverTimeoutProcessing(60_000L);
    }

    @Test
    void timeout_recovery_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(reviewRequestInboxProcessor)
                .recoverTimeoutProcessing(60_000L);

        // when & then
        assertThatCode(() -> worker.recoverTimeoutReviewRequestInbox()).doesNotThrowAnyException();
    }

    @Test
    void processingTimeoutMs가_0이하면_생성자에서_예외가_발생한다() {
        assertThatThrownBy(() -> new ReviewRequestInboxTimeoutRecoveryWorker(reviewRequestInboxProcessor, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingTimeoutMs는 0보다 커야 합니다.");
    }
}
