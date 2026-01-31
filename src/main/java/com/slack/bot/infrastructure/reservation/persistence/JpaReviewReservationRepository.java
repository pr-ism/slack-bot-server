package com.slack.bot.infrastructure.reservation.persistence;

import com.slack.bot.domain.reservation.ReviewReservation;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewReservationRepository extends ListCrudRepository<ReviewReservation, Long> {
}
