package com.slack.bot.application.command.dto.request;

public record SlackCommandRequest(String text, String userId, String teamId) {
}
