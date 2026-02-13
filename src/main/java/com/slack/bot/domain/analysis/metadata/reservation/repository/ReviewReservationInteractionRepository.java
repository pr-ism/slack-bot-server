package com.slack.bot.domain.analysis.metadata.reservation.repository;

import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import java.time.Instant;
import java.util.Optional;

public interface ReviewReservationInteractionRepository {

    Optional<ReviewReservationInteraction> findByReviewKey(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    );

    boolean updateReviewTimeSelectedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewTimeSelectedAt
    );

    boolean increaseScheduleChangeCount(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    );

    boolean increaseScheduleCancelCount(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    );

    boolean updateReviewScheduledAtAndPullRequestNotifiedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewScheduledAt,
            Instant pullRequestNotifiedAt
    );

    boolean markReviewFulfilledAndUpdatePullRequestNotifiedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant pullRequestNotifiedAt
    );

    ReviewReservationInteraction create(ReviewReservationInteraction interaction);
}
