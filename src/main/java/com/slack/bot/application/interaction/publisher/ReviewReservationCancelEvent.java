package com.slack.bot.application.interaction.publisher;

public record ReviewReservationCancelEvent(
        String teamId,
        String channelId,
        String slackUserId,
        Long reservationId,
        Long projectId,
        Long githubPullRequestId
) implements ReviewInteractionEvent {
}
