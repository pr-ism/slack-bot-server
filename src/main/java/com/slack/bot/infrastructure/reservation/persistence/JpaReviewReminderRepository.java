package com.slack.bot.infrastructure.reservation.persistence;

import com.slack.bot.domain.reservation.ReviewReminder;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaReviewReminderRepository extends ListCrudRepository<ReviewReminder, Long> {

    Optional<ReviewReminder> findByReservationId(Long reservationId);

    void deleteByReservationId(Long reservationId);
}
