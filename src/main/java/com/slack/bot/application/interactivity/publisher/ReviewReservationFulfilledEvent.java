package com.slack.bot.application.interactivity.publisher;

import java.time.Instant;

public record ReviewReservationFulfilledEvent(
        String teamId,
        Long projectId,
        String slackUserId,
        Long pullRequestId,
        Instant pullRequestNotifiedAt
) implements ReviewInteractionEvent {
}
