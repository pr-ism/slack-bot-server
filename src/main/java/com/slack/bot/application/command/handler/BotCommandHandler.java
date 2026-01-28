package com.slack.bot.application.command.handler;

import com.slack.bot.application.command.dto.SlackCommandContextDto;

public interface BotCommandHandler {

    String handle(SlackCommandContextDto context);
}
