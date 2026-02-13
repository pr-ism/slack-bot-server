package com.slack.bot.infrastructure.analysis.metadata.reservation.persistence;

import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewReservationInteractionRepository extends ListCrudRepository<ReviewReservationInteraction, Long> {
}
