package com.slack.bot.application.interaction.publisher;

public record ReviewReservationRequestEvent(
        String teamId,
        String channelId,
        String slackUserId,
        Long projectId,
        Long githubPullRequestId,
        String metaJson
) implements ReviewInteractionEvent {
}
