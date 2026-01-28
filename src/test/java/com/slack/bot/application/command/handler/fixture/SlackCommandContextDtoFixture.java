package com.slack.bot.application.command.handler.fixture;

import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.application.command.dto.SlackParsedCommand;
import com.slack.bot.application.command.dto.request.SlackCommandRequest;
import java.util.List;

public final class SlackCommandContextDtoFixture {

    public static SlackCommandContextDto create(List<String> arguments) {
        String text = String.join(" ", arguments);
        SlackCommandRequest request = new SlackCommandRequest(text, "U1", "T1");
        String commandName = "";

        if (!arguments.isEmpty()) {
            commandName = arguments.getFirst();
        }

        SlackParsedCommand parsed = SlackParsedCommand.create(text, arguments, commandName);

        return SlackCommandContextDto.create(request, parsed);
    }

    public static SlackCommandContextDto create(
            String text,
            String userId,
            String teamId,
            List<String> arguments,
            String commandName
    ) {
        SlackCommandRequest request = new SlackCommandRequest(text, userId, teamId);
        SlackParsedCommand parsed = SlackParsedCommand.create(text, arguments, commandName);

        return SlackCommandContextDto.create(request, parsed);
    }

    private SlackCommandContextDtoFixture() {
    }
}
