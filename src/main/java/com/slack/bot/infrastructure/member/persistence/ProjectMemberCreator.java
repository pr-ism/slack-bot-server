package com.slack.bot.infrastructure.member.persistence;

import com.slack.bot.domain.member.ProjectMember;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectMemberCreator {

    private final JpaProjectMemberRepository projectMemberRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNew(ProjectMember member) {
        projectMemberRepository.save(member);
        entityManager.flush();
    }
}
