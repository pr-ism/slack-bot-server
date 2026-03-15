package com.slack.bot.application.interaction.publisher;

import java.time.Instant;

public record ReviewReservationFulfilledEvent(
        String teamId,
        Long projectId,
        String slackUserId,
        Long githubPullRequestId,
        Instant pullRequestNotifiedAt
) implements ReviewInteractionEvent {
}
