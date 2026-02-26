package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewNotificationOutboxCreator {

    private final EntityManager entityManager;
    private final JpaReviewNotificationOutboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNew(ReviewNotificationOutbox outbox) {
        repository.save(outbox);
        entityManager.flush();
    }
}
