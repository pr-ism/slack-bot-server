package com.slack.bot.domain.member.repository;

import com.slack.bot.domain.member.ProjectMember;
import java.util.Optional;

public interface ProjectMemberRepository {

    void save(ProjectMember projectMember);

    Optional<ProjectMember> findById(Long id);

    Optional<ProjectMember> findBySlackUser(String teamId, String slackUserId);

    Optional<ProjectMember> findByGithubUser(String teamId, String githubId);
}
