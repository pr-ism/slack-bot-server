package com.slack.bot.infrastructure.reservation.persistence;

import com.slack.bot.domain.reservation.ReviewReservationInteraction;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewReservationInteractionRepository extends ListCrudRepository<ReviewReservationInteraction, Long> {
}
