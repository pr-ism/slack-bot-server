package com.slack.bot.application.command.dto;

import com.slack.bot.application.command.dto.request.SlackCommandRequest;
import java.util.List;

public record SlackCommandContextDto(
        String text,
        String userId,
        String teamId,
        String rawText,
        List<String> arguments,
        String commandName
) {
    public static SlackCommandContextDto create(SlackCommandRequest request, SlackParsedCommand parsed) {
        return new SlackCommandContextDto(
                request.text(),
                request.userId(),
                request.teamId(),
                parsed.rawText(),
                parsed.arguments(),
                parsed.commandName()
        );
    }
}
