package com.slack.bot.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.domain.reservation.vo.ReminderDestination;
import com.slack.bot.domain.reservation.vo.ReminderFiredTime;
import com.slack.bot.domain.reservation.vo.ReminderParticipants;
import com.slack.bot.domain.reservation.vo.ReminderPullRequest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReminderTest {

    @Test
    void 리뷰_리마인더를_초기화한다() {
        // given
        Instant scheduledAt = Instant.now();
        ReminderDestination destination = ReminderDestination.builder()
                                                             .teamId("T")
                                                             .channelId("C")
                                                             .build();
        ReminderParticipants participants = ReminderParticipants.builder()
                                                                .pullRequestAuthorSlackId("A")
                                                                .reviewerSlackId("R")
                                                                .build();
        ReminderPullRequest pullRequest = ReminderPullRequest.builder()
                                                             .pullRequestTitle("Title")
                                                             .pullRequestUrl("URL")
                                                             .build();

        // when
        ReviewReminder reminder = assertDoesNotThrow(
                () -> ReviewReminder.builder()
                                    .reservationId(1L)
                                    .scheduledAt(scheduledAt)
                                    .destination(destination)
                                    .participants(participants)
                                    .pullRequest(pullRequest)
                                    .firedTime(ReminderFiredTime.notFired())
                                    .build()
        );

        // then
        assertAll(
                () -> assertThat(reminder.getReservationId()).isEqualTo(1L),
                () -> assertThat(reminder.getScheduledAt()).isEqualTo(scheduledAt),
                () -> assertThat(reminder.getDestination()).isEqualTo(destination),
                () -> assertThat(reminder.getParticipants()).isEqualTo(participants),
                () -> assertThat(reminder.getPullRequest()).isEqualTo(pullRequest),
                () -> assertThat(reminder.getFiredTime()).isEqualTo(ReminderFiredTime.notFired()),
                () -> assertThat(reminder.isFired()).isFalse()
        );
    }

    @Test
    void firedTime을_입력하지_않으면_기본적으로_NotFired_상태이다() {
        // given
        ReviewReminder reminder = ReviewReminder.builder()
                                                .reservationId(1L)
                                                .scheduledAt(Instant.now())
                                                .build();

        // when & then
        assertAll(
                () -> assertThat(reminder.getFiredTime()).isNotNull(),
                () -> assertThat(reminder.getFiredTime()).isEqualTo(ReminderFiredTime.notFired()),
                () -> assertThat(reminder.isFired()).isFalse()
        );
    }

    @Test
    void 리마인더_발송_완료_시간을_마킹한다() {
        // given
        ReviewReminder reminder = ReviewReminder.builder()
                                                .reservationId(1L)
                                                .scheduledAt(Instant.now())
                                                .firedTime(ReminderFiredTime.notFired())
                                                .build();
        Instant firedAt = Instant.now();

        // when
        reminder.markFired(firedAt);

        // then
        assertAll(
                () -> assertThat(reminder.isFired()).isTrue(),
                () -> assertThat(reminder.getFiredTime().getValue()).isEqualTo(firedAt)
        );
    }
}
