package com.slack.bot.domain.reservation.repository;

import com.slack.bot.domain.reservation.ReviewReminder;
import java.util.Optional;

public interface ReviewReminderRepository {

    ReviewReminder save(ReviewReminder reminder);

    Optional<ReviewReminder> findByReservationId(Long reservationId);

    void deleteByReservationId(Long reservationId);
}
