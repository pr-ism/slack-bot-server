package com.slack.bot.application.interactivity.client.dto.response;

public record SlackChatPostMessageResponse(
        boolean ok,
        String error
) {
}
