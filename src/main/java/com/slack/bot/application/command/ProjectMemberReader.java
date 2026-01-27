package com.slack.bot.application.command;

import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectMemberReader {

    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public Optional<ProjectMember> read(String teamId, String slackUserId) {
        return projectMemberRepository.findBySlackUser(teamId, slackUserId);
    }
}
