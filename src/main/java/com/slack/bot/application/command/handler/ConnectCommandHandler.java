package com.slack.bot.application.command.handler;

import com.slack.bot.application.command.MemberConnector;
import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.global.config.properties.CommandMessageProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectCommandHandler implements BotCommandHandler {

    private final MemberConnector memberService;
    private final CommandMessageProperties messages;

    public static ConnectCommandHandler create(
            MemberConnector memberConnector,
            CommandMessageProperties messages
    ) {
        return new ConnectCommandHandler(memberConnector, messages);
    }

    private ConnectCommandHandler(MemberConnector memberConnector, CommandMessageProperties messages) {
        this.memberService = memberConnector;
        this.messages = messages;
    }

    @Override
    public String handle(SlackCommandContextDto context) {
        List<String> arguments = context.arguments();

        if (arguments.size() < 2) {
            return messages.connect()
                           .missingGithubId();
        }

        String githubId = arguments.get(1);
        String teamId = context.teamId();
        String userId = context.userId();

        try {
            String displayName = memberService.connectUser(teamId, userId, githubId);

            return messages.connect()
                           .success()
                           .formatted(displayName, githubId);
        } catch (Exception e) {
            log.warn("Exception : ", e);
            return messages.connect()
                           .fail();
        }
    }
}
