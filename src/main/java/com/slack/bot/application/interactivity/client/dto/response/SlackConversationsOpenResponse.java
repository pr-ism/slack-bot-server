package com.slack.bot.application.interactivity.client.dto.response;

public record SlackConversationsOpenResponse(
        boolean ok,
        String error,
        SlackChannel channel
) {

    public record SlackChannel(String id) {
    }
}
