package com.slack.bot.application.interactivity.workflow;

import com.slack.bot.application.command.MemberConnector;
import com.slack.bot.application.command.ProjectMemberReader;
import com.slack.bot.application.interactivity.notification.NotificationDispatcher;
import com.slack.bot.global.config.properties.ClaimMappingMessageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClaimMappingWorkflow {

    private static final String ALREADY_CONNECTED_MESSAGE_TEMPLATE = "이미 GitHub ID가 등록되어 있습니다. (GitHub: %s)";

    private final MemberConnector memberConnector;
    private final ProjectMemberReader projectMemberReader;
    private final NotificationDispatcher notificationDispatcher;
    private final ClaimMappingMessageProperties messages;

    public void handle(String teamId, String slackUserId, String githubId, String token, String channelId) {
        String mappedGithubId = findMappedGithubId(teamId, slackUserId);

        if (mappedGithubId != null) {
            String alreadyConnectedMessage = ALREADY_CONNECTED_MESSAGE_TEMPLATE.formatted(mappedGithubId);

            notificationDispatcher.sendEphemeral(token, channelId, slackUserId, alreadyConnectedMessage);
            notificationDispatcher.sendDirectMessageIfEnabled(teamId, token, slackUserId, alreadyConnectedMessage);
            return;
        }

        memberConnector.connectUser(teamId, slackUserId, githubId);

        String successMessage = String.format(messages.success(), githubId);

        notificationDispatcher.sendEphemeral(token, channelId, slackUserId, successMessage);
        notificationDispatcher.sendDirectMessageIfEnabled(teamId, token, slackUserId, successMessage);
    }

    private String findMappedGithubId(String teamId, String slackUserId) {
        return projectMemberReader.read(teamId, slackUserId)
                                  .map(projectMember -> projectMember.getGithubId())
                                  .map(registeredGithubId -> registeredGithubId == null ? null : registeredGithubId.getValue())
                                  .filter(registeredGithubId -> !registeredGithubId.isBlank())
                                  .orElse(null);
    }
}
