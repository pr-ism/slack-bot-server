package com.slack.bot.infrastructure.reservation.persistence;

import com.slack.bot.domain.reservation.ReviewReminder;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewReminderRepository extends ListCrudRepository<ReviewReminder, Long> {
}
