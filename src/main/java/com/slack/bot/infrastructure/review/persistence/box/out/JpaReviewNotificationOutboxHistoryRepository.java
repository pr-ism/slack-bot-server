package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewNotificationOutboxHistoryRepository
        extends ListCrudRepository<ReviewNotificationOutboxHistory, Long> {
}
