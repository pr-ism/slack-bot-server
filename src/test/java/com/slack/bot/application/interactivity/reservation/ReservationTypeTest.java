package com.slack.bot.application.interactivity.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.reservation.dto.ReservationCommandDto;
import com.slack.bot.application.interactivity.reservation.dto.ReservationContextDto;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class ReservationTypeTest {

    @Mock
    ReviewReservationCoordinator coordinator;

    @Test
    void 예약_ID가_없으면_신규_예약으로_판단한다() {
        // when
        ReservationType actual = ReservationType.resolve(null);

        // then
        assertThat(actual).isEqualTo(ReservationType.NEW);
    }

    @Test
    void 예약_ID가_있으면_재스케줄로_판단한다() {
        // when
        ReservationType actual = ReservationType.resolve(1L);

        // then
        assertThat(actual).isEqualTo(ReservationType.RESCHEDULE);
    }

    @Test
    void NEW_타입은_신규_예약이다() {
        // when
        boolean actual = ReservationType.NEW.isNew();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void RESCHEDULE_타입은_신규_예약이_아니다() {
        // when
        boolean actual = ReservationType.RESCHEDULE.isNew();

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void 신규_예약_완료_메시지를_반환한다() {
        // when
        String actual = ReservationType.NEW.successMessage();

        // then
        assertThat(actual).isEqualTo("리뷰 예약이 완료되었습니다.");
    }

    @Test
    void 예약_변경_완료_메시지를_반환한다() {
        // when
        String actual = ReservationType.RESCHEDULE.successMessage();

        // then
        assertThat(actual).isEqualTo("리뷰 예약 시간이 변경되었습니다.");
    }

    @Test
    void NEW_타입은_예약_생성을_수행한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .build();

        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://example.com")
                .build();

        ReservationContextDto context = ReservationContextDto.builder()
                .meta(meta)
                .reviewerId("U2")
                .token("token")
                .scheduledAt(Instant.now())
                .authorSlackId("U1")
                .isReschedule(false)
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .reservationId(null)
                .build();

        ReviewReservation mockReservation = ReviewReservation.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(Instant.now())
                .status(ReservationStatus.ACTIVE)
                .build();

        given(coordinator.create(any(ReservationCommandDto.class))).willReturn(mockReservation);

        // when
        ReservationType.NEW.persist(coordinator, context);

        // then
        verify(coordinator).create(any(ReservationCommandDto.class));
    }

    @Test
    void RESCHEDULE_타입은_예약_변경을_수행한다() {
        // given
        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                .teamId("T1")
                .channelId("C1")
                .build();

        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://example.com")
                .build();

        ReservationContextDto context = ReservationContextDto.builder()
                .meta(meta)
                .reviewerId("U2")
                .token("token")
                .scheduledAt(Instant.now())
                .authorSlackId("U1")
                .isReschedule(true)
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .reservationId(10L)
                .build();

        ReviewReservation mockReservation = ReviewReservation.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(Instant.now())
                .status(ReservationStatus.ACTIVE)
                .build();

        given(coordinator.reschedule(any(ReservationCommandDto.class))).willReturn(mockReservation);

        // when
        ReservationType.RESCHEDULE.persist(coordinator, context);

        // then
        verify(coordinator).reschedule(any(ReservationCommandDto.class));
    }
}
