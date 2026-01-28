package com.slack.bot.application.command.handler;

import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.global.config.properties.CommandMessageProperties;

public class UnknownCommandHandler implements BotCommandHandler {

    private final CommandMessageProperties messages;

    public static UnknownCommandHandler create(CommandMessageProperties messages) {
        return new UnknownCommandHandler(messages);
    }

    private UnknownCommandHandler(CommandMessageProperties messages) {
        this.messages = messages;
    }

    @Override
    public String handle(SlackCommandContextDto ignored) {
        return messages.unknown();
    }
}
