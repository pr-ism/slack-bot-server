package com.slack.bot.infrastructure.analysis.metadata.reservation.persistence;

import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewReservationInteractionCreator {

    private final EntityManager entityManager;
    private final JpaReviewReservationInteractionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReviewReservationInteraction create(ReviewReservationInteraction interaction) {
        ReviewReservationInteraction saved = repository.save(interaction);

        entityManager.flush();
        return saved;
    }
}
