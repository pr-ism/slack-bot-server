package com.slack.bot.application.interactivity.reminder.dto;

import com.slack.bot.application.interactivity.reservation.dto.ReservationCommandDto;
import com.slack.bot.domain.reservation.ReviewReminder;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReminderDestination;
import com.slack.bot.domain.reservation.vo.ReminderParticipants;
import com.slack.bot.domain.reservation.vo.ReminderPullRequest;
import java.time.Instant;
import lombok.Builder;

@Builder
public record ReminderScheduleCommandDto(
        Long reservationId,
        Instant scheduledAt,
        String teamId,
        String channelId,
        String pullRequestAuthorSlackId,
        String reviewerSlackId,
        String pullRequestUrl,
        String pullRequestTitle
) {

    public static ReminderScheduleCommandDto from(ReviewReservation reservation, ReservationCommandDto command) {
        return builder().reservationId(reservation.getId())
                        .scheduledAt(command.scheduledAt())
                        .teamId(command.teamId())
                        .channelId(command.channelId())
                        .pullRequestAuthorSlackId(command.authorSlackId())
                        .reviewerSlackId(command.reviewerSlackId())
                        .pullRequestUrl(command.reservationPullRequest().getPullRequestUrl())
                        .pullRequestTitle(command.reservationPullRequest().getPullRequestTitle())
                        .build();
    }

    public ReviewReminder toReminder() {
        return ReviewReminder.builder()
                             .reservationId(reservationId)
                             .scheduledAt(scheduledAt)
                             .destination(ReminderDestination.builder()
                                                             .teamId(teamId)
                                                             .channelId(channelId)
                                                             .build())
                             .participants(ReminderParticipants.builder()
                                                               .pullRequestAuthorSlackId(pullRequestAuthorSlackId)
                                                               .reviewerSlackId(reviewerSlackId)
                                                               .build())
                             .pullRequest(ReminderPullRequest.builder()
                                                             .pullRequestUrl(pullRequestUrl)
                                                             .pullRequestTitle(pullRequestTitle)
                                                             .build())
                             .build();
    }
}
