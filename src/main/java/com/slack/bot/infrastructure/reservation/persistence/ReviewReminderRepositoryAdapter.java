package com.slack.bot.infrastructure.reservation.persistence;

import com.slack.bot.domain.reservation.ReviewReminder;
import com.slack.bot.domain.reservation.repository.ReviewReminderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewReminderRepositoryAdapter implements ReviewReminderRepository {

    private final JpaReviewReminderRepository jpaReviewReminderRepository;

    @Override
    @Transactional
    public ReviewReminder save(ReviewReminder reminder) {
        return jpaReviewReminderRepository.save(reminder);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewReminder> findByReservationId(Long reservationId) {
        return jpaReviewReminderRepository.findByReservationId(reservationId);
    }

    @Override
    @Transactional
    public void deleteByReservationId(Long reservationId) {
        jpaReviewReminderRepository.deleteByReservationId(reservationId);
    }
}
