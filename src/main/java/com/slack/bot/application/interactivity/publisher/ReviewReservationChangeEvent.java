package com.slack.bot.application.interactivity.publisher;

public record ReviewReservationChangeEvent(
        String teamId,
        String channelId,
        String slackUserId,
        Long reservationId
) implements ReviewInteractionEvent {
}
