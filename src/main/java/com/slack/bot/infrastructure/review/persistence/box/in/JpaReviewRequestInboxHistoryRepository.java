package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewRequestInboxHistoryRepository
        extends ListCrudRepository<ReviewRequestInboxHistory, Long> {
}
