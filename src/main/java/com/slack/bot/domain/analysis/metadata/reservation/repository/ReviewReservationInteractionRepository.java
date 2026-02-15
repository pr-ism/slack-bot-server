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

    void recordReviewTimeSelected(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewTimeSelectedAt
    );

    void recordScheduleChanged(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    );

    void recordScheduleCanceled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    );

    void recordReviewScheduled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewScheduledAt,
            Instant pullRequestNotifiedAt
    );

    void recordReviewFulfilled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant pullRequestNotifiedAt
    );

    ReviewReservationInteraction create(ReviewReservationInteraction interaction);
}
