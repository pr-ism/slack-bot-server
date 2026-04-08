package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewRequestInboxHistoryRepository
        extends ListCrudRepository<ReviewRequestInboxHistoryJpaEntity, Long> {

    default List<ReviewRequestInboxHistory> findAllDomains() {
        return findAll().stream()
                        .map(history -> history.toDomain())
                        .toList();
    }
}
