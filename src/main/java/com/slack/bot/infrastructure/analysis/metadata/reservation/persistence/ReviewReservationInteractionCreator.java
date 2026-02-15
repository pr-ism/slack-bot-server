package com.slack.bot.infrastructure.analysis.metadata.reservation.persistence;

import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReviewReservationInteractionCreator {

    private final EntityManager entityManager;
    private final SimpleJpaRepository<ReviewReservationInteraction, Long> repository;

    public ReviewReservationInteractionCreator(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.repository = new SimpleJpaRepository<>(ReviewReservationInteraction.class, entityManager);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReviewReservationInteraction create(ReviewReservationInteraction interaction) {
        ReviewReservationInteraction saved = repository.save(interaction);

        entityManager.flush();
        return saved;
    }
}
