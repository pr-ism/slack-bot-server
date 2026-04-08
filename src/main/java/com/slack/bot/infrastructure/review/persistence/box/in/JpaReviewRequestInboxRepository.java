package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewRequestInboxRepository extends ListCrudRepository<ReviewRequestInboxJpaEntity, Long> {

    default Optional<ReviewRequestInbox> findDomainById(Long id) {
        return findById(id).map(inbox -> inbox.toDomain());
    }

    default List<ReviewRequestInbox> findAllDomains() {
        return findAll().stream()
                        .map(inbox -> inbox.toDomain())
                        .toList();
    }
}
