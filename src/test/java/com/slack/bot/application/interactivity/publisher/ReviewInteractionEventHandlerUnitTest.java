package com.slack.bot.application.interactivity.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewInteractionEventHandlerUnitTest {

    @Mock
    MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;

    @Mock
    ReviewReservationInteractionRepository reviewReservationInteractionRepository;

    @Test
    void 리뷰_예약_변경_이벤트_저장_경합으로_중복_생성이_발생하면_업데이트를_재시도한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                mysqlDuplicateKeyDetector,
                reviewReservationInteractionRepository
        );
        ReviewReservationChangeEvent event = new ReviewReservationChangeEvent("T1", "C1", "U1", 100L, 123L, 10L);
        DataIntegrityViolationException duplicateException = new DataIntegrityViolationException("duplicate");

        given(reviewReservationInteractionRepository.increaseScheduleChangeCount("T1", 123L, 10L, "U1"))
                .willReturn(false, true);
        given(reviewReservationInteractionRepository.create(any(ReviewReservationInteraction.class)))
                .willThrow(duplicateException);
        given(mysqlDuplicateKeyDetector.isNotDuplicateKey(duplicateException)).willReturn(false);

        // when
        handler.handleReviewReservationChangeEvent(event);

        // then
        verify(reviewReservationInteractionRepository, times(2))
                .increaseScheduleChangeCount("T1", 123L, 10L, "U1");
    }

    @Test
    void 리뷰_예약_취소_이벤트_저장_경합으로_중복_생성이_발생하면_업데이트를_재시도한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                mysqlDuplicateKeyDetector,
                reviewReservationInteractionRepository
        );
        ReviewReservationCancelEvent event = new ReviewReservationCancelEvent("T1", "C1", "U1", 100L, 123L, 10L);
        DataIntegrityViolationException duplicateException = new DataIntegrityViolationException("duplicate");

        given(reviewReservationInteractionRepository.increaseScheduleCancelCount("T1", 123L, 10L, "U1"))
                .willReturn(false, true);
        given(reviewReservationInteractionRepository.create(any(ReviewReservationInteraction.class)))
                .willThrow(duplicateException);
        given(mysqlDuplicateKeyDetector.isNotDuplicateKey(duplicateException)).willReturn(false);

        // when
        handler.handleReviewReservationCancelEvent(event);

        // then
        verify(reviewReservationInteractionRepository, times(2))
                .increaseScheduleCancelCount("T1", 123L, 10L, "U1");
    }

    @Test
    void 리뷰_예약_확정_이벤트_저장_경합으로_중복_생성이_발생하면_업데이트를_재시도한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-02-13T00:00:00Z"), ZoneOffset.UTC);
        ReviewInteractionEventHandler handler = new ReviewInteractionEventHandler(
                clock,
                mysqlDuplicateKeyDetector,
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
        DataIntegrityViolationException duplicateException = new DataIntegrityViolationException("duplicate");

        given(reviewReservationInteractionRepository.updateReviewScheduledAtAndPullRequestNotifiedAt(
                "T1",
                123L,
                10L,
                "U1",
                Instant.parse("2026-02-14T01:00:00Z"),
                Instant.parse("2026-02-14T00:30:00Z")
        )).willReturn(false, true);
        given(reviewReservationInteractionRepository.create(any(ReviewReservationInteraction.class)))
                .willThrow(duplicateException);
        given(mysqlDuplicateKeyDetector.isNotDuplicateKey(duplicateException)).willReturn(false);

        // when
        handler.handleReviewReservationScheduledEvent(event);

        // then
        verify(reviewReservationInteractionRepository, times(2))
                .updateReviewScheduledAtAndPullRequestNotifiedAt(
                        "T1",
                        123L,
                        10L,
                        "U1",
                        Instant.parse("2026-02-14T01:00:00Z"),
                        Instant.parse("2026-02-14T00:30:00Z")
                );
    }
}
