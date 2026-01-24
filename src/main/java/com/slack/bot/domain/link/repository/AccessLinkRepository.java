package com.slack.bot.domain.link.repository;

import com.slack.bot.domain.link.AccessLink;
import com.slack.bot.domain.member.ProjectMember;
import java.util.Optional;

public interface AccessLinkRepository {

    void save(AccessLink link);

    Optional<AccessLink> findByProjectMemberId(Long projectMemberId);

    Optional<ProjectMember> findProjectMemberByLinkKey(String linkKey);
}
