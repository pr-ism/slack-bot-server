package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewRequestInboxCreator {

    private final EntityManager entityManager;
    private final JpaReviewRequestInboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNew(ReviewRequestInbox inbox) {
        repository.save(inbox);
        entityManager.flush();
    }
}
