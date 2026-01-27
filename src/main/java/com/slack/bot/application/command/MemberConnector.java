package com.slack.bot.application.command;

import com.slack.bot.application.command.client.MemberConnectionSlackApiClient;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberConnector {

    private final WorkspaceRepository workspaceRepository;
    private final MemberConnectionSlackApiClient memberConnectionSlackApiClient;
    private final MemberConnectionWriter memberConnectionWriter;

    public String connectUser(String teamId, String slackUserId, String githubId) {
        Workspace workspace = workspaceRepository.findByTeamId(teamId)
                                                 .orElseThrow(() -> new WorkspaceNotFoundException());
        String accessToken = workspace.getAccessToken();
        String slackDisplayName = memberConnectionSlackApiClient.resolveUserName(accessToken, slackUserId);

        return memberConnectionWriter.saveOrUpdateMember(
                teamId,
                slackUserId,
                slackDisplayName,
                githubId
        );
    }
}
