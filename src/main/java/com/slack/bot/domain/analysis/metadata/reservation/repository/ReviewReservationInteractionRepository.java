package com.slack.bot.domain.analysis.metadata.reservation.repository;

import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import java.util.Optional;

public interface ReviewReservationInteractionRepository {

    ReviewReservationInteraction save(ReviewReservationInteraction interaction);

    Optional<ReviewReservationInteraction> findByReviewKey(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    );
}
