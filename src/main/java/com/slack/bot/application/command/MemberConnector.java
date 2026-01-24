package com.slack.bot.application.command;

import com.slack.bot.application.command.client.MemberConnectionSlackApiClient;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import com.slack.bot.domain.member.vo.GithubId;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberConnector {

    private final WorkspaceRepository workspaceRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MemberConnectionSlackApiClient memberConnectionSlackApiClient;

    @Transactional
    public String connectUser(String teamId, String slackUserId, String githubId) {
        Workspace workspace = workspaceRepository.findByTeamId(teamId)
                                                 .orElseThrow(() -> new WorkspaceNotFoundException());

        return projectMemberRepository.findBySlackUser(teamId, slackUserId)
                                    .map(projectMember -> updateMemberGithubId(projectMember, githubId))
                                    .orElseGet(
                                            () -> createMemberAndConnectGithubId(
                                                    workspace,
                                                    teamId,
                                                    slackUserId,
                                                    githubId
                                            )
                                    );
    }

    private String updateMemberGithubId(ProjectMember projectMember, String githubId) {
        GithubId newGithubId = GithubId.create(githubId);

        projectMember.connectGithubId(newGithubId);
        return projectMember.getDisplayName();
    }

    private String createMemberAndConnectGithubId(Workspace workspace, String teamId, String slackUserId, String githubId) {
        String accessToken = workspace.getAccessToken();
        String slackDisplayName = memberConnectionSlackApiClient.resolveUserName(accessToken, slackUserId);
        ProjectMember projectMember = ProjectMember.builder()
                                                   .teamId(teamId)
                                                   .slackUserId(slackUserId)
                                                   .displayName(slackDisplayName)
                                                   .build();
        GithubId newGithubId = GithubId.create(githubId);

        projectMember.connectGithubId(newGithubId);
        projectMemberRepository.save(projectMember);
        return slackDisplayName;
    }
}
