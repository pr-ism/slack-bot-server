package com.slack.bot.application.interactivity.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.reminder.dto.ReminderScheduleCommandDto;
import com.slack.bot.domain.reservation.ReviewReminder;
import com.slack.bot.domain.reservation.repository.ReviewReminderRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class ReviewReminderSchedulerTest {

    @Mock
    TaskScheduler taskScheduler;

    @Mock
    ReviewReminderRepository reviewReminderRepository;

    @Mock
    ReviewReminderDispatcher reviewReminderDispatcher;

    private ReviewReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReviewReminderScheduler(taskScheduler, reviewReminderRepository, reviewReminderDispatcher);
    }

    @Test
    void 예약을_저장하고_스케줄러에_등록한다() {
        // given
        ReminderScheduleCommandDto command = new ReminderScheduleCommandDto(
                10L,
                Instant.parse("2024-02-01T10:15:30Z"),
                "T1",
                "C1",
                "U-AUTHOR",
                "U-REVIEWER",
                "https://github.com/org/repo/pull/1",
                "Add feature"
        );
        ReviewReminder reminder = command.toReminder();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);

        given(reviewReminderRepository.save(any())).willReturn(reminder);
        given(taskScheduler.schedule(runnableCaptor.capture(), instantCaptor.capture()))
                .willReturn(null);
        given(reviewReminderRepository.findByReservationId(10L)).willReturn(Optional.of(reminder));

        // when
        scheduler.schedule(command);

        // then
        assertAll(
                () -> verify(reviewReminderRepository).save(any()),
                () -> assertThat(instantCaptor.getValue()).isEqualTo(command.scheduledAt())
        );

        runnableCaptor.getValue().run();
        verify(reviewReminderDispatcher).send(reminder);
    }

    @Test
    void 예약_ID로_취소하면_리포지토리를_통해_삭제한다() {
        // given
        Long reservationId = 20L;

        // when
        scheduler.cancelByReservationId(reservationId);

        // then
        verify(reviewReminderRepository).deleteByReservationId(reservationId);
    }
}
