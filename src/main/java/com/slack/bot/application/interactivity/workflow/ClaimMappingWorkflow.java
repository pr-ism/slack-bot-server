package com.slack.bot.application.interactivity.workflow;

import com.slack.bot.application.command.MemberConnector;
import com.slack.bot.application.interactivity.notification.NotificationDispatcher;
import com.slack.bot.global.config.properties.ClaimMappingMessageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClaimMappingWorkflow {

    private final MemberConnector memberConnector;
    private final NotificationDispatcher notificationDispatcher;
    private final ClaimMappingMessageProperties messages;

    public void handle(String teamId, String slackUserId, String githubId, String token, String channelId) {
        memberConnector.connectUser(teamId, slackUserId, githubId);

        String successMessage = String.format(messages.success(), githubId);

        notificationDispatcher.sendEphemeral(token, channelId, slackUserId, successMessage);
        notificationDispatcher.sendDirectMessageIfEnabled(teamId, token, slackUserId, successMessage);
    }
}
