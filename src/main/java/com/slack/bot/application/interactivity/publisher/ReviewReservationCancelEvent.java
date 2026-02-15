package com.slack.bot.application.interactivity.publisher;

public record ReviewReservationCancelEvent(
        String teamId,
        String channelId,
        String slackUserId,
        Long reservationId,
        Long projectId,
        Long pullRequestId
) implements ReviewInteractionEvent {
}
