package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
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
class ReviewRequestInboxWorkerTest {

    @Mock
    ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    ReviewRequestInboxWorker reviewRequestInboxWorker;

    @BeforeEach
    void setUp() {
        reviewRequestInboxWorker = new ReviewRequestInboxWorker(reviewRequestInboxProcessor);

        ReflectionTestUtils.setField(reviewRequestInboxWorker, "workerEnabled", true);
        ReflectionTestUtils.setField(reviewRequestInboxWorker, "batchSize", 30);
        ReflectionTestUtils.setField(reviewRequestInboxWorker, "processingTimeoutMs", 60_000L);
    }

    @Test
    void worker_enabled가_true면_pending을_처리한다() {
        // when
        reviewRequestInboxWorker.processPendingReviewRequestInbox();

        // then
        verify(reviewRequestInboxProcessor).processPending(30, 60_000L);
    }

    @Test
    void worker_enabled가_false면_처리하지_않는다() {
        // given
        ReflectionTestUtils.setField(reviewRequestInboxWorker, "workerEnabled", false);

        // when
        reviewRequestInboxWorker.processPendingReviewRequestInbox();

        // then
        verify(reviewRequestInboxProcessor, never()).processPending(30, 60_000L);
    }

    @Test
    void worker_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        willThrow(new RuntimeException("worker failure"))
                .given(reviewRequestInboxProcessor)
                .processPending(30, 60_000L);

        // when & then
        assertThatCode(() -> reviewRequestInboxWorker.processPendingReviewRequestInbox())
                .doesNotThrowAnyException();
    }
}
