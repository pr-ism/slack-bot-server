package com.slack.bot.application.interactivity.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.reservation.dto.ReservationCommandDto;
import com.slack.bot.application.interactivity.reservation.exception.ActiveReservationAlreadyExistsException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationKeyMismatchException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationNotFoundException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationScheduleInPastException;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReminder;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReminderRepository;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReservationCoordinatorTest {

    @Autowired
    ReviewReservationCoordinator coordinator;

    @Autowired
    ReviewReservationRepository reservationRepository;

    @Autowired
    ReviewReminderRepository reminderRepository;

    @Test
    void 활성_예약을_조회한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant scheduledAt = futureInstant(3600);
        ReservationCommandDto command = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(scheduledAt)
                .build();

        ReviewReservation created = coordinator.create(command);

        // when
        Optional<ReviewReservation> actual = coordinator.findActive("T1", 1L, "U2");

        // then
        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getId()).isEqualTo(created.getId())
        );
    }

    @Test
    void 활성_예약이_없으면_빈_Optional을_반환한다() {
        // when
        Optional<ReviewReservation> actual = coordinator.findActive("T1", 1L, "U_UNKNOWN");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void ID로_예약을_조회한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant scheduledAt = futureInstant(3600);
        ReservationCommandDto command = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(scheduledAt)
                .build();

        ReviewReservation created = coordinator.create(command);

        // when
        Optional<ReviewReservation> actual = coordinator.findById(created.getId());

        // then
        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getId()).isEqualTo(created.getId())
        );
    }

    @Test
    void 존재하지_않는_ID로_조회하면_빈_Optional을_반환한다() {
        // when
        Optional<ReviewReservation> actual = coordinator.findById(999L);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void 새로운_예약을_생성한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(42)
                .pullRequestTitle("Fix bug")
                .pullRequestUrl("https://github.com/org/repo/pull/42")
                .build();

        ReservationCommandDto command = ReservationCommandDto.builder()
                .teamId("T123")
                .channelId("C456")
                .projectId(10L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U789")
                .reviewerSlackId("U012")
                .scheduledAt(futureInstant(3600))
                .build();

        // when
        ReviewReservation actual = coordinator.create(command);

        // then
        assertAll(
                () -> assertThat(actual.getId()).isNotNull(),
                () -> assertThat(actual.getTeamId()).isEqualTo("T123"),
                () -> assertThat(actual.getChannelId()).isEqualTo("C456"),
                () -> assertThat(actual.getProjectId()).isEqualTo(10L),
                () -> assertThat(actual.getAuthorSlackId()).isEqualTo("U789"),
                () -> assertThat(actual.getReviewerSlackId()).isEqualTo("U012"),
                () -> assertThat(actual.getScheduledAt()).isAfter(Instant.now()),
                () -> assertThat(actual.getStatus()).isEqualTo(ReservationStatus.ACTIVE)
        );
    }

    @Test
    void 이미_활성화된_예약이_있으면_예외가_발생한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("First")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        ReservationCommandDto firstCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(futureInstant(3600))
                .build();

        coordinator.create(firstCommand);

        ReservationPullRequest secondPullRequest = ReservationPullRequest.builder()
                .pullRequestId(2L)
                .pullRequestNumber(2)
                .pullRequestTitle("Second")
                .pullRequestUrl("https://github.com/org/repo/pull/2")
                .build();

        ReservationCommandDto secondCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(secondPullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(futureInstant(7200))
                .build();

        // when & then
        assertThatThrownBy(() -> coordinator.create(secondCommand))
                .isInstanceOf(ActiveReservationAlreadyExistsException.class)
                .hasMessageContaining("이미 활성화된 리뷰 예약이 있습니다");
    }

    @Test
    void 예약_시간이_현재보다_이전이면_생성에_실패한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Past")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        ReservationCommandDto command = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(pastInstant(60))
                .build();

        // when & then
        assertThatThrownBy(() -> coordinator.create(command))
                .isInstanceOf(ReservationScheduleInPastException.class)
                .hasMessageContaining("리뷰 예약 시간은 현재보다 이후여야 합니다");
    }

    @Test
    void 예약을_재스케줄한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Original")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant originalScheduledAt = futureInstant(3600);
        ReservationCommandDto originalCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(originalScheduledAt)
                .build();

        ReviewReservation original = coordinator.create(originalCommand);
        coordinator.cancel(original.getId());

        ReservationPullRequest newPullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Updated")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant rescheduledAt = futureInstant(7200);
        ReservationCommandDto rescheduleCommand = ReservationCommandDto.builder()
                .reservationId(original.getId())
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(newPullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(rescheduledAt)
                .build();

        // when
        ReviewReservation actual = coordinator.reschedule(rescheduleCommand);

        // then
        assertAll(
                () -> assertThat(actual.getId()).isNotEqualTo(original.getId()),
                () -> assertThat(actual.getScheduledAt()).isEqualTo(rescheduledAt),
                () -> assertThat(actual.getStatus()).isEqualTo(ReservationStatus.ACTIVE)
        );

        // 원래 예약은 취소됨
        Optional<ReviewReservation> originalReservation = reservationRepository.findById(original.getId());
        assertAll(
                () -> assertThat(originalReservation).isPresent(),
                () -> assertThat(originalReservation.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED)
        );
    }

    @Test
    void 활성_예약이_동일_ID면_일정과_리마인더가_갱신된다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Original")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant originalScheduledAt = futureInstant(3600);
        ReservationCommandDto originalCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(originalScheduledAt)
                .build();

        ReviewReservation original = coordinator.create(originalCommand);

        Instant rescheduledAt = futureInstant(7200);
        ReservationCommandDto rescheduleCommand = ReservationCommandDto.builder()
                .reservationId(original.getId())
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(rescheduledAt)
                .build();

        // when
        ReviewReservation actual = coordinator.reschedule(rescheduleCommand);

        // then
        Optional<ReviewReminder> reminder = reminderRepository.findByReservationId(original.getId());
        assertAll(
                () -> assertThat(actual.getId()).isEqualTo(original.getId()),
                () -> assertThat(actual.getScheduledAt()).isEqualTo(rescheduledAt),
                () -> assertThat(actual.getStatus()).isEqualTo(ReservationStatus.ACTIVE),
                () -> assertThat(reminder).isPresent(),
                () -> assertThat(reminder.get().getScheduledAt()).isEqualTo(rescheduledAt)
        );
    }

    @Test
    void 다른_ID의_활성_예약이_있으면_예외가_발생한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Original")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant firstScheduledAt = futureInstant(3600);
        ReservationCommandDto firstCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(firstScheduledAt)
                .build();

        ReviewReservation first = coordinator.create(firstCommand);
        coordinator.cancel(first.getId());

        Instant secondScheduledAt = futureInstant(5400);
        ReservationCommandDto secondCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(secondScheduledAt)
                .build();

        ReviewReservation second = coordinator.create(secondCommand);

        Instant rescheduledAt = futureInstant(7200);
        ReservationCommandDto rescheduleCommand = ReservationCommandDto.builder()
                .reservationId(first.getId())
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(rescheduledAt)
                .build();

        // when & then
        Optional<ReviewReservation> activeReservation = reservationRepository.findById(second.getId());
        assertAll(
                () -> assertThatThrownBy(() -> coordinator.reschedule(rescheduleCommand))
                        .isInstanceOf(ActiveReservationAlreadyExistsException.class)
                        .hasMessageContaining("이미 활성화된 리뷰 예약이 있습니다"),
                () -> assertThat(activeReservation).isPresent(),
                () -> assertThat(activeReservation.get().getStatus()).isEqualTo(ReservationStatus.ACTIVE)
        );
    }

    @Test
    void 존재하지_않는_예약을_재스케줄하면_예외가_발생한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        ReservationCommandDto command = ReservationCommandDto.builder()
                .reservationId(999L)
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(futureInstant(3600))
                .build();

        // when & then
        assertThatThrownBy(() -> coordinator.reschedule(command))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("예약을 찾을 수 없습니다");
    }

    @Test
    void 예약_시간이_현재보다_이전이면_재스케줄에_실패한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Original")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant originalScheduledAt = futureInstant(3600);
        ReservationCommandDto originalCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(originalScheduledAt)
                .build();

        ReviewReservation original = coordinator.create(originalCommand);

        ReservationCommandDto rescheduleCommand = ReservationCommandDto.builder()
                .reservationId(original.getId())
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(pastInstant(60))
                .build();

        // when & then
        assertThatThrownBy(() -> coordinator.reschedule(rescheduleCommand))
                .isInstanceOf(ReservationScheduleInPastException.class)
                .hasMessageContaining("리뷰 예약 시간은 현재보다 이후여야 합니다");
    }

    @Test
    void 다른_프로젝트의_예약을_재스케줄하면_예외가_발생한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        Instant originalScheduledAt = futureInstant(3600);
        ReservationCommandDto originalCommand = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(originalScheduledAt)
                .build();

        ReviewReservation original = coordinator.create(originalCommand);

        ReservationCommandDto rescheduleCommand = ReservationCommandDto.builder()
                .reservationId(original.getId())
                .teamId("T1")
                .channelId("C1")
                .projectId(999L) // 다른 프로젝트
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(futureInstant(7200))
                .build();

        // when & then
        assertThatThrownBy(() -> coordinator.reschedule(rescheduleCommand))
                .isInstanceOf(ReservationKeyMismatchException.class)
                .hasMessageContaining("예약 정보와 프로젝트/리뷰어가 일치하지 않습니다");
    }

    @Test
    void 예약을_취소한다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        ReservationCommandDto command = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(futureInstant(3600))
                .build();

        ReviewReservation created = coordinator.create(command);

        // when
        Optional<ReviewReservation> actual = coordinator.cancel(created.getId());

        // then
        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED)
        );
    }

    @Test
    void 존재하지_않는_예약을_취소하면_빈_Optional을_반환한다() {
        // when
        Optional<ReviewReservation> actual = coordinator.cancel(999L);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void 이미_취소된_예약을_다시_취소해도_상태가_유지된다() {
        // given
        ReservationPullRequest pullRequest = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("Test")
                .pullRequestUrl("https://github.com/org/repo/pull/1")
                .build();

        ReservationCommandDto command = ReservationCommandDto.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pullRequest)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(futureInstant(3600))
                .build();

        ReviewReservation created = coordinator.create(command);
        coordinator.cancel(created.getId());

        // when
        Optional<ReviewReservation> actual = coordinator.cancel(created.getId());

        // then
        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED)
        );
    }

    private Instant futureInstant(long offsetSeconds) {
        return Instant.now().plusSeconds(offsetSeconds);
    }

    private Instant pastInstant(long offsetSeconds) {
        return Instant.now().minusSeconds(offsetSeconds);
    }
}
