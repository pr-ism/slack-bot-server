package com.slack.bot.application.command.dto;

import java.util.List;

public record SlackParsedCommand(
        String rawText,
        List<String> arguments,
        String commandName
) {
    public static SlackParsedCommand emptyArguments(String rawText) {
        return new SlackParsedCommand(rawText, List.of(), "");
    }

    public static SlackParsedCommand create(String rawText, List<String> arguments, String commandName) {
        return new SlackParsedCommand(rawText, arguments, commandName);
    }
}
