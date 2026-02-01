package com.slack.bot.application.interactivity.reminder;

import com.slack.bot.application.interactivity.reminder.dto.ReminderScheduleCommandDto;
import com.slack.bot.domain.reservation.repository.ReviewReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReviewReminderScheduler {

    private final TaskScheduler taskScheduler;
    private final ReviewReminderRepository reviewReminderRepository;
    private final ReviewReminderDispatcher reviewReminderDispatcher;

    @Transactional
    public void schedule(ReminderScheduleCommandDto command) {
        reviewReminderRepository.save(command.toReminder());
        taskScheduler.schedule(() -> fire(command.reservationId()), command.scheduledAt());
    }

    @Transactional
    public void cancelByReservationId(Long reservationId) {
        reviewReminderRepository.deleteByReservationId(reservationId);
    }

    private void fire(Long reservationId) {
        reviewReminderRepository.findByReservationId(reservationId)
                .ifPresent(reviewReminder -> reviewReminderDispatcher.send(reviewReminder));
    }
}
