package com.slack.bot.application.review.box.in;

import static org.mockito.Mockito.verify;

import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
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
        reviewRequestInboxWorker = new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                30,
                1_000L,
                30_000L
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
    void wake_up_hint와_stop은_예외없이_동작한다() {
        reviewRequestInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.REVIEW_REQUEST_INBOX));
        reviewRequestInboxWorker.stop();
    }

    @Test
    void batchSize가_0이하면_생성자에서_예외가_발생한다() {
        assertThatThrownBy(() -> new ReviewRequestInboxWorker(reviewRequestInboxProcessor, 0, 1_000L, 30_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize는 0보다 커야 합니다.");
    }
}
