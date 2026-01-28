package com.slack.bot.application.command.handler;

import com.slack.bot.application.command.AccessLinker;
import com.slack.bot.application.command.MemberConnector;
import com.slack.bot.application.command.ProjectMemberReader;
import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.global.config.properties.AppProperties;
import com.slack.bot.global.config.properties.CommandMessageProperties;
import java.util.Map;
import java.util.Objects;

public class CommandHandlerRegistry {

    private final Map<String, BotCommandHandler> commands;
    private final BotCommandHandler unknownCommand;

    public static CommandHandlerRegistry create(
            MemberConnector memberService,
            ProjectMemberReader projectMemberReader,
            AccessLinker accessLinker,
            AppProperties appProperties,
            CommandMessageProperties commandMessageProperties
    ) {
        Map<String, BotCommandHandler> commands = buildCommands(
                memberService,
                projectMemberReader,
                accessLinker,
                appProperties,
                commandMessageProperties
        );
        BotCommandHandler unknownCommand = UnknownCommandHandler.create(commandMessageProperties);
        return new CommandHandlerRegistry(commands, unknownCommand);
    }

    private CommandHandlerRegistry(Map<String, BotCommandHandler> commands, BotCommandHandler unknownCommand) {
        this.commands = commands;
        this.unknownCommand = unknownCommand;
    }

    private static Map<String, BotCommandHandler> buildCommands(
            MemberConnector memberService,
            ProjectMemberReader projectMemberReader,
            AccessLinker accessLinker,
            AppProperties appProperties,
            CommandMessageProperties commandMessageProperties
    ) {
        HelpCommandHandler helpCommand = HelpCommandHandler.create(commandMessageProperties);
        ConnectCommandHandler connectCommand = ConnectCommandHandler.create(memberService, commandMessageProperties);
        MyPageCommandHandler myPageCommand = MyPageCommandHandler.create(
                projectMemberReader,
                accessLinker,
                appProperties,
                commandMessageProperties
        );

        return Map.of(
                "help", helpCommand,
                "connect", connectCommand,
                "my-page", myPageCommand,
                "mypage", myPageCommand
        );
    }

    public String handle(SlackCommandContextDto context) {
        String key = context.commandName();
        BotCommandHandler command = resolve(key);

        return command.handle(context);
    }

    private BotCommandHandler resolve(String key) {
        if (key == null) {
            return unknownCommand;
        }

        return Objects.requireNonNullElse(commands.get(key), unknownCommand);
    }
}
