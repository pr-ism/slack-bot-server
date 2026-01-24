package com.slack.bot.infrastructure.link.persistence;

import static com.slack.bot.domain.link.QAccessLink.accessLink;
import static com.slack.bot.domain.member.QProjectMember.projectMember;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.link.AccessLink;
import com.slack.bot.domain.link.repository.AccessLinkRepository;
import com.slack.bot.domain.member.ProjectMember;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AccessLinkRepositoryAdapter implements AccessLinkRepository {

    private final JpaAccessLinkRepository accessLinkRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    @Transactional
    public void save(AccessLink link) {
        accessLinkRepository.save(link);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectMember> findProjectMemberByLinkKey(String linkKey) {
        ProjectMember result = queryFactory.selectFrom(projectMember)
                                           .join(accessLink)
                                           .on(accessLink.projectMemberId.eq(projectMember.id))
                                           .where(accessLink.linkKey.eq(linkKey))
                                           .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessLink> findByProjectMemberId(Long projectMemberId) {
        return accessLinkRepository.findByProjectMemberId(projectMemberId);
    }
}
