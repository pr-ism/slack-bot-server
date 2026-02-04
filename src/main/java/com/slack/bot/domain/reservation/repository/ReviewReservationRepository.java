package com.slack.bot.domain.reservation.repository;

import com.slack.bot.domain.reservation.ReviewReservation;
import java.util.Optional;

public interface ReviewReservationRepository {

    ReviewReservation save(ReviewReservation reservation);

    Optional<ReviewReservation> findById(Long reservationId);

    Optional<ReviewReservation> findActive(String teamId, Long projectId, String reviewerSlackId);

    Optional<ReviewReservation> findActiveForUpdate(String teamId, Long projectId, String reviewerSlackId);

    boolean existsActive(String teamId, Long projectId, String reviewerSlackId);
}
