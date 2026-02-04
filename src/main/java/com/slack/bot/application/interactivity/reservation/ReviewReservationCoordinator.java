package com.slack.bot.application.interactivity.reservation;

import com.slack.bot.application.interactivity.reminder.ReviewReminderScheduler;
import com.slack.bot.application.interactivity.reminder.dto.ReminderScheduleCommandDto;
import com.slack.bot.application.interactivity.reservation.dto.ReservationCommandDto;
import com.slack.bot.application.interactivity.reservation.exception.ActiveReservationAlreadyExistsException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationKeyMismatchException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationNotFoundException;
import com.slack.bot.application.interactivity.reservation.exception.ReservationScheduleInPastException;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReviewReservationCoordinator {

    private final Clock clock;
    private final ReviewReminderScheduler reviewReminderScheduler;
    private final ReviewReservationRepository reviewReservationRepository;

    public Optional<ReviewReservation> findActive(
            String teamId,
            Long projectId,
            String reviewerSlackId
    ) {
        return reviewReservationRepository.findActive(teamId, projectId, reviewerSlackId);
    }

    public Optional<ReviewReservation> findById(Long reservationId) {
        return reviewReservationRepository.findById(reservationId);
    }

    @Transactional
    public ReviewReservation create(ReservationCommandDto command) {
        validateScheduledAt(command.scheduledAt());
        return reviewReservationRepository.findActiveForUpdate(
                        command.teamId(),
                        command.projectId(),
                        command.reviewerSlackId()
                )
                .<ReviewReservation>map(existing -> {
                    throw new ActiveReservationAlreadyExistsException("이미 활성화된 리뷰 예약이 있습니다.");
                })
                .orElseGet(() -> createInternal(command));
    }

    @Transactional
    public ReviewReservation reschedule(ReservationCommandDto command) {
        ReviewReservation existing = requireReservation(command.reservationId());

        validateSameKey(existing, command.teamId(), command.projectId(), command.reviewerSlackId());
        validateScheduledAt(command.scheduledAt());

        return reviewReservationRepository.findActiveForUpdate(
                        command.teamId(),
                        command.projectId(),
                        command.reviewerSlackId()
                )
                .map(active -> {
                    if (!active.getId().equals(existing.getId())) {
                        throw new ActiveReservationAlreadyExistsException("이미 활성화된 리뷰 예약이 있습니다.");
                    }
                    return rescheduleInternal(active, command);
                })
                .orElseGet(() -> {
                    cancelInternal(existing);
                    return createInternal(command);
                });
    }

    private ReviewReservation createInternal(ReservationCommandDto command) {
        ReviewReservation reservation = ReviewReservation.builder()
                                                         .teamId(command.teamId())
                                                         .channelId(command.channelId())
                                                         .projectId(command.projectId())
                                                         .reservationPullRequest(command.reservationPullRequest())
                                                         .authorSlackId(command.authorSlackId())
                                                         .reviewerSlackId(command.reviewerSlackId())
                                                         .scheduledAt(command.scheduledAt())
                                                         .status(ReservationStatus.ACTIVE)
                                                         .build();
        ReviewReservation savedReservation = reviewReservationRepository.save(reservation);
        ReminderScheduleCommandDto reminderScheduleCommand = ReminderScheduleCommandDto.from(savedReservation, command);

        reviewReminderScheduler.schedule(reminderScheduleCommand);
        return savedReservation;
    }

    private ReviewReservation rescheduleInternal(ReviewReservation reservation, ReservationCommandDto command) {
        reservation.reschedule(command.scheduledAt());
        reviewReservationRepository.save(reservation);

        reviewReminderScheduler.cancelByReservationId(reservation.getId());
        ReminderScheduleCommandDto reminderScheduleCommand = ReminderScheduleCommandDto.from(reservation, command);
        reviewReminderScheduler.schedule(reminderScheduleCommand);

        return reservation;
    }

    @Transactional
    public Optional<ReviewReservation> cancel(Long reservationId) {
        return reviewReservationRepository.findById(reservationId)
                .map(existing -> {
                    cancelInternal(existing);
                    return existing;
                });
    }

    private ReviewReservation requireReservation(Long reservationId) {
        return reviewReservationRepository
                .findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("예약을 찾을 수 없습니다: " + reservationId));
    }

    private void cancelInternal(ReviewReservation reservation) {
        if (!reservation.isActive()) {
            return;
        }

        reviewReminderScheduler.cancelByReservationId(reservation.getId());
        reservation.markCancelled();
        reviewReservationRepository.save(reservation);
    }

    private void validateSameKey(
            ReviewReservation existing,
            String teamId,
            Long projectId,
            String reviewerSlackId
    ) {
        if (safeNotEquals(existing.getTeamId(), teamId)
                || safeNotEquals(existing.getProjectId(), projectId)
                || safeNotEquals(existing.getReviewerSlackId(), reviewerSlackId)) {
            throw new ReservationKeyMismatchException("예약 정보와 프로젝트/리뷰어가 일치하지 않습니다.");
        }
    }

    private boolean safeNotEquals(Object left, Object right) {
        return !Objects.equals(left, right);
    }

    private void validateScheduledAt(Instant scheduledAt) {
        Instant now = Instant.now(clock);

        if (!scheduledAt.isAfter(now)) {
            throw new ReservationScheduleInPastException("리뷰 예약 시간은 현재보다 이후여야 합니다.");
        }
    }
}
