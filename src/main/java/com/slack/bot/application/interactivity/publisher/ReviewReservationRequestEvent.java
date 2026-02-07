package com.slack.bot.application.interactivity.publisher;

public record ReviewReservationRequestEvent(
        String teamId,
        String channelId,
        String slackUserId,
        String metaJson
) implements ReviewInteractionEvent {
}
