package com.slack.bot.application.interactivity.publisher;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewInteractionEventHandlerUnitTest {

    @Mock
    ReviewReservationInteractionRepository reviewReservationInteractionRepository;

    @Test
    void 리뷰_예약_변경_이벤트를_처리하면_변경_횟수_업서트를_요청한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                reviewReservationInteractionRepository
        );
        ReviewReservationChangeEvent event = new ReviewReservationChangeEvent("T1", "C1", "U1", 100L, 123L, 10L);

        // when
        handler.handleReviewReservationChangeEvent(event);

        // then
        verify(reviewReservationInteractionRepository)
                .recordScheduleChanged("T1", 123L, 10L, "U1");
    }

    @Test
    void 리뷰_예약_취소_이벤트를_처리하면_취소_횟수_업서트를_요청한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                reviewReservationInteractionRepository
        );
        ReviewReservationCancelEvent event = new ReviewReservationCancelEvent("T1", "C1", "U1", 100L, 123L, 10L);

        // when
        handler.handleReviewReservationCancelEvent(event);

        // then
        verify(reviewReservationInteractionRepository)
                .recordScheduleCanceled("T1", 123L, 10L, "U1");
    }

    @Test
    void 리뷰_예약_확정_이벤트를_처리하면_확정_시각_업서트를_요청한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                reviewReservationInteractionRepository
        );
        ReviewReservationScheduledEvent event = new ReviewReservationScheduledEvent(
                "T1",
                "C1",
                "U1",
                100L,
                123L,
                10L,
                Instant.parse("2026-02-14T01:00:00Z"),
                Instant.parse("2026-02-14T00:30:00Z")
        );

        // when
        handler.handleReviewReservationScheduledEvent(event);

        // then
        verify(reviewReservationInteractionRepository)
                .recordReviewScheduled(
                        "T1",
                        123L,
                        10L,
                        "U1",
                        Instant.parse("2026-02-14T01:00:00Z"),
                        Instant.parse("2026-02-14T00:30:00Z")
                );
    }

    @Test
    void pullRequest_알림_시각이_없으면_현재_시각으로_업서트를_요청한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                reviewReservationInteractionRepository
        );
        ReviewReservationFulfilledEvent event = new ReviewReservationFulfilledEvent(
                "T1",
                123L,
                "U1",
                10L,
                null
        );

        // when
        handler.handleReviewReservationFulfilledEvent(event);

        // then
        verify(reviewReservationInteractionRepository)
                .recordReviewFulfilled(
                        "T1",
                        123L,
                        10L,
                        "U1",
                        Instant.parse("2026-02-13T00:00:00Z")
                );
    }

    @Test
    void 리뷰_키가_유효하지_않으면_업서트를_요청하지_않는다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                reviewReservationInteractionRepository
        );
        ReviewReservationRequestEvent event = new ReviewReservationRequestEvent(
                "T1",
                "C1",
                "",
                123L,
                10L,
                "{\"meta\":\"request\"}"
        );

        // when
        handler.handleReviewReservationRequestEvent(event);

        // then
        verifyNoInteractions(reviewReservationInteractionRepository);
    }
}
