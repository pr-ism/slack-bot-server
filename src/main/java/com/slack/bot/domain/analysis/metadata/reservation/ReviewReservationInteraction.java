package com.slack.bot.domain.analysis.metadata.reservation;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.domain.analysis.metadata.reservation.vo.ReviewReservationInteractionCount;
import com.slack.bot.domain.analysis.metadata.reservation.vo.ReviewReservationInteractionTimeline;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_reservation_interactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReservationInteraction extends BaseTimeEntity {

    private String teamId;

    private Long projectId;

    private Long pullRequestId;

    private String reviewerSlackId;

    @Embedded
    private ReviewReservationInteractionTimeline interactionTimeline;

    @Embedded
    private ReviewReservationInteractionCount interactionCount;

    private boolean reviewFulfilled;

    public static ReviewReservationInteraction create(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        validate(teamId, projectId, pullRequestId, reviewerSlackId);

        return new ReviewReservationInteraction(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId,
                ReviewReservationInteractionTimeline.defaults(),
                ReviewReservationInteractionCount.defaults(),
                false
        );
    }

    private static void validate(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        validateTeamId(teamId);
        validateProjectId(projectId);
        validatePullRequestId(pullRequestId);
        validateReviewerSlackId(reviewerSlackId);
    }

    private static void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId는 비어 있을 수 없습니다.");
        }
    }

    private static void validatePullRequestId(Long pullRequestId) {
        if (pullRequestId == null) {
            throw new IllegalArgumentException("pullRequestId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateReviewerSlackId(String reviewerSlackId) {
        if (reviewerSlackId == null || reviewerSlackId.isBlank()) {
            throw new IllegalArgumentException("reviewerSlackId는 비어 있을 수 없습니다.");
        }
    }

    private ReviewReservationInteraction(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            ReviewReservationInteractionTimeline interactionTimeline,
            ReviewReservationInteractionCount interactionCount,
            boolean reviewFulfilled
    ) {
        this.teamId = teamId;
        this.projectId = projectId;
        this.pullRequestId = pullRequestId;
        this.reviewerSlackId = reviewerSlackId;
        this.interactionTimeline = interactionTimeline;
        this.interactionCount = interactionCount;
        this.reviewFulfilled = reviewFulfilled;
    }

    public void recordReviewScheduledAt(Instant reviewScheduledAt) {
        this.interactionTimeline = interactionTimeline.recordReviewScheduledAt(reviewScheduledAt);
    }

    public void recordReviewTimeSelectedAt(Instant reviewTimeSelectedAt) {
        this.interactionTimeline = interactionTimeline.recordReviewTimeSelectedAt(reviewTimeSelectedAt);
    }

    public void recordPullRequestNotifiedAt(Instant pullRequestNotifiedAt) {
        this.interactionTimeline = interactionTimeline.recordPullRequestNotifiedAt(pullRequestNotifiedAt);
    }

    public void increaseScheduleCancelCount() {
        interactionCount.increaseScheduleCancelCount();
    }

    public void increaseScheduleChangeCount() {
        interactionCount.increaseScheduleChangeCount();
    }

    public void markReviewFulfilled() {
        this.reviewFulfilled = true;
    }
}
