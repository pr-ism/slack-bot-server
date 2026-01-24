package com.slack.bot.application.command;

import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import com.slack.bot.domain.member.vo.GithubId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberConnectionWriter {

    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public String saveOrUpdateMember(String teamId, String slackUserId, String displayName, String githubId) {
        return projectMemberRepository.findBySlackUser(teamId, slackUserId)
                                      .map(projectMember -> updateMemberGithubId(projectMember, githubId))
                                      .orElseGet(
                                              () -> createMemberAndConnectGithubId(
                                                      teamId,
                                                      slackUserId,
                                                      displayName,
                                                      githubId
                                              )
                                      );
    }

    private String updateMemberGithubId(ProjectMember projectMember, String githubId) {
        GithubId newGithubId = GithubId.create(githubId);

        projectMember.connectGithubId(newGithubId);
        return projectMember.getDisplayName();
    }

    private String createMemberAndConnectGithubId(
            String teamId,
            String slackUserId,
            String displayName,
            String githubId
    ) {
        GithubId newGithubId = GithubId.create(githubId);
        ProjectMember projectMember = ProjectMember.builder()
                                                   .teamId(teamId)
                                                   .slackUserId(slackUserId)
                                                   .displayName(displayName)
                                                   .build();

        projectMember.connectGithubId(newGithubId);
        ProjectMember savedMember = projectMemberRepository.save(projectMember);

        return savedMember.getDisplayName();
    }
}
