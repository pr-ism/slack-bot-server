package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewNotificationOutboxRepository
        extends ListCrudRepository<ReviewNotificationOutboxJpaEntity, Long> {

    default Optional<ReviewNotificationOutbox> findDomainById(Long id) {
        return findById(id).map(outbox -> outbox.toDomain());
    }

    default List<ReviewNotificationOutbox> findAllDomains() {
        return findAll().stream()
                        .map(outbox -> outbox.toDomain())
                        .toList();
    }
}
