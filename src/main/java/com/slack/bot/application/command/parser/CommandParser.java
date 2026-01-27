package com.slack.bot.application.command.parser;

import com.slack.bot.application.command.dto.SlackParsedCommand;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CommandParser {

    public SlackParsedCommand parse(String text) {
        if (text == null || text.isBlank()) {
            return SlackParsedCommand.emptyArguments("");
        }

        String[] tokens = splitTokens(text);
        List<String> arguments = Arrays.asList(tokens);
        String commandName = parseCommandName(tokens);
        return SlackParsedCommand.create(text, arguments, commandName);
    }

    private String[] splitTokens(String text) {
        String trimmed = text.trim();

        return trimmed.split("\\s+");
    }

    private String parseCommandName(String[] tokens) {
        String commandName = "";

        if (tokens.length > 0) {
            commandName = tokens[0];
        }

        return commandName;
    }
}
