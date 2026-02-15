package com.slack.bot.application.interactivity.publisher;

import java.time.Instant;

public record ReviewReservationScheduledEvent(
        String teamId,
        String channelId,
        String slackUserId,
        Long reservationId,
        Long projectId,
        Long pullRequestId,
        Instant reviewScheduledAt,
        Instant pullRequestNotifiedAt
) implements ReviewInteractionEvent {
}
