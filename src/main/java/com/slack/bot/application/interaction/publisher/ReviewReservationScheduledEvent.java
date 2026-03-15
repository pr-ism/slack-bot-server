package com.slack.bot.application.interaction.publisher;

import java.time.Instant;

public record ReviewReservationScheduledEvent(
        String teamId,
        String channelId,
        String slackUserId,
        Long reservationId,
        Long projectId,
        Long githubPullRequestId,
        Instant reviewScheduledAt,
        Instant pullRequestNotifiedAt
) implements ReviewInteractionEvent {
}
