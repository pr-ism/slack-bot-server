package com.slack.bot.application.interaction.client.dto.response;

public record SlackChatPostMessageResponse(
        boolean ok,
        String error
) {
}
