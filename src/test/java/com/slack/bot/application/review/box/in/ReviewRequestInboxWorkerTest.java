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
class ReviewRequestInboxWorkerTest {

    @Mock
    ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    ReviewRequestInboxWorker reviewRequestInboxWorker;

    @BeforeEach
    void setUp() {
        reviewRequestInboxWorker = new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 30);
    }

    @Test
    void worker는_pending을_처리한다() {
        // when
        reviewRequestInboxWorker.processPendingReviewRequestInbox();

        // then
        verify(reviewRequestInboxProcessor).processPending(30);
    }

    @Test
    void worker_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(reviewRequestInboxProcessor)
                .processPending(30);

        // when & then
        assertThatCode(() -> reviewRequestInboxWorker.processPendingReviewRequestInbox())
                .doesNotThrowAnyException();
    }

    @Test
    void batchSize가_0이하면_생성자에서_예외가_발생한다() {
        assertThatThrownBy(() -> new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize는 0보다 커야 합니다.");
    }
}
