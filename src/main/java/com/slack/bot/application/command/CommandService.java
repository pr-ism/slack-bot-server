package com.slack.bot.application.command;

import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.application.command.dto.SlackParsedCommand;
import com.slack.bot.application.command.dto.request.SlackCommandRequest;
import com.slack.bot.application.command.handler.CommandHandlerRegistry;
import com.slack.bot.application.command.parser.CommandParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandService {

    private final CommandParser commandParser;
    private final CommandHandlerRegistry commandRegistry;

    public String handle(SlackCommandRequest request) {
        SlackParsedCommand parsed = commandParser.parse(request.text());
        SlackCommandContextDto context = SlackCommandContextDto.create(request, parsed);

        return commandRegistry.handle(context);
    }
}
