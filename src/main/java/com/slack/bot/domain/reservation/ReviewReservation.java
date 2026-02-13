package com.slack.bot.domain.reservation;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReservation extends BaseTimeEntity {

    private String teamId;

    private String channelId;

    private Long projectId;

    @Embedded
    private ReservationPullRequest reservationPullRequest;

    private String authorSlackId;

    private String reviewerSlackId;

    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Builder
    private ReviewReservation(
            String teamId,
            String channelId,
            Long projectId,
            ReservationPullRequest reservationPullRequest,
            String authorSlackId,
            String reviewerSlackId,
            Instant scheduledAt,
            ReservationStatus status
    ) {
        validateTeamId(teamId);
        validateChannelId(channelId);
        validateProjectId(projectId);
        validateReservationPullRequest(reservationPullRequest);
        validateAuthorSlackId(authorSlackId);
        validateReviewerSlackId(reviewerSlackId);
        validateScheduledAt(scheduledAt);
        validateStatus(status);

        this.teamId = teamId;
        this.channelId = channelId;
        this.projectId = projectId;
        this.reservationPullRequest = reservationPullRequest;
        this.authorSlackId = authorSlackId;
        this.reviewerSlackId = reviewerSlackId;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }

    public boolean isActive() {
        return status != null && status.isActive();
    }

    public void markCancelled() {
        status = ReservationStatus.CANCELLED;
    }

    public void reschedule(Instant scheduledAt) {
        validateScheduledAt(scheduledAt);

        this.scheduledAt = scheduledAt;
    }

    public boolean isNotEqualTo(ReviewReservation other) {
        if (other == null) {
            return true;
        }

        return !Objects.equals(getId(), other.getId());
    }

    private void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId는 비어 있을 수 없습니다.");
        }
    }

    private void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId는 비어 있을 수 없습니다.");
        }
    }

    private void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId는 비어 있을 수 없습니다.");
        }
    }

    private void validateReservationPullRequest(ReservationPullRequest reservationPullRequest) {
        if (reservationPullRequest == null) {
            throw new IllegalArgumentException("예약 대상 PR 정보는 비어 있을 수 없습니다.");
        }
    }

    private void validateAuthorSlackId(String authorSlackId) {
        if (authorSlackId == null || authorSlackId.isBlank()) {
            throw new IllegalArgumentException("authorSlackId는 비어 있을 수 없습니다.");
        }
    }

    private void validateReviewerSlackId(String reviewerSlackId) {
        if (reviewerSlackId == null || reviewerSlackId.isBlank()) {
            throw new IllegalArgumentException("reviewerSlackId는 비어 있을 수 없습니다.");
        }
    }

    private void validateScheduledAt(Instant scheduledAt) {
        if (scheduledAt == null) {
            throw new IllegalArgumentException("리뷰 예약 시간은 비어 있을 수 없습니다.");
        }
    }

    private void validateStatus(ReservationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("예약 상태는 비어 있을 수 없습니다.");
        }
    }
}
