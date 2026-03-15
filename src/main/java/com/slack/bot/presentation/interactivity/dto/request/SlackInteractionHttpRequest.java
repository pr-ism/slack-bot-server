package com.slack.bot.presentation.interactivity.dto.request;

public record SlackInteractionHttpRequest(
        String timestamp,
        String signature,
        String rawBody,
        String payloadJson
) {
}
