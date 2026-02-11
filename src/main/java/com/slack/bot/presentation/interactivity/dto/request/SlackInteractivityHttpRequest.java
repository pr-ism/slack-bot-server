package com.slack.bot.presentation.interactivity.dto.request;

public record SlackInteractivityHttpRequest(
        String timestamp,
        String signature,
        String rawBody,
        String payloadJson
) {
}
