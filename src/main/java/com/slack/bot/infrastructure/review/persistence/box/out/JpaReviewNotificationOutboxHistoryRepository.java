package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewNotificationOutboxHistoryRepository
        extends ListCrudRepository<ReviewNotificationOutboxHistoryJpaEntity, Long> {

    default List<ReviewNotificationOutboxHistory> findAllDomains() {
        return findAll().stream()
                        .map(history -> history.toDomain())
                        .toList();
    }
}
