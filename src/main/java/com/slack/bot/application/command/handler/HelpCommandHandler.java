package com.slack.bot.application.command.handler;

import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.global.config.properties.CommandMessageProperties;

public class HelpCommandHandler implements BotCommandHandler {

    private final CommandMessageProperties messages;

    private HelpCommandHandler(CommandMessageProperties messages) {
        this.messages = messages;
    }

    public static HelpCommandHandler create(CommandMessageProperties messages) {
        return new HelpCommandHandler(messages);
    }

    @Override
    public String handle(SlackCommandContextDto ignored) {
        return messages.help();
    }
}
