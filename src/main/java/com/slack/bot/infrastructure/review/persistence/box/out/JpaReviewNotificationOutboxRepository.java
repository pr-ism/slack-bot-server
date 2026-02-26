package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewNotificationOutboxRepository extends ListCrudRepository<ReviewNotificationOutbox, Long> {
}
