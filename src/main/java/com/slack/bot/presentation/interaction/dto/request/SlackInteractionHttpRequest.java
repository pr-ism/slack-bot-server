package com.slack.bot.presentation.interaction.dto.request;

public record SlackInteractionHttpRequest(
        String timestamp,
        String signature,
        String rawBody,
        String payloadJson
) {
}
