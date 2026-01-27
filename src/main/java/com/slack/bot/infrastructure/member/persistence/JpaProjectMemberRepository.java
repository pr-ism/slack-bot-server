package com.slack.bot.infrastructure.member.persistence;

import com.slack.bot.domain.member.ProjectMember;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaProjectMemberRepository extends ListCrudRepository<ProjectMember, Long> {
}
