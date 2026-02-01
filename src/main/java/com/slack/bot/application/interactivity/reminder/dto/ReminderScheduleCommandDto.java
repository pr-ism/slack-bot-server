package com.slack.bot.application.interactivity.reminder.dto;

import com.slack.bot.domain.reservation.ReviewReminder;
import com.slack.bot.domain.reservation.vo.ReminderDestination;
import com.slack.bot.domain.reservation.vo.ReminderParticipants;
import com.slack.bot.domain.reservation.vo.ReminderPullRequest;
import java.time.Instant;

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
