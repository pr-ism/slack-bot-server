package com.slack.bot.application.interactivity.publisher;

public record ReviewReservationRequestEvent(
        String teamId,
        String channelId,
        String slackUserId,
        Long projectId,
        Long githubPullRequestId,
        String metaJson
) implements ReviewInteractionEvent {
}
