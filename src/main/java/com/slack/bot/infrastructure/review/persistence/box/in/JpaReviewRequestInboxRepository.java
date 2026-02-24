package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewRequestInboxRepository extends ListCrudRepository<ReviewRequestInbox, Long> {
}
