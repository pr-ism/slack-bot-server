package com.slack.bot.infrastructure.member.persistence;

import static com.slack.bot.domain.member.QProjectMember.projectMember;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectMemberRepositoryAdapter implements ProjectMemberRepository {

    private final JPAQueryFactory queryFactory;
    private final ProjectMemberCreator projectMemberCreator;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;

    @Override
    @Transactional
    public ProjectMember save(ProjectMember member) {
        try {
            projectMemberCreator.saveNew(member);
            return member;
        } catch (DataIntegrityViolationException ex) {
            ProjectMember existing = resolveDuplicate(member, ex);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    private ProjectMember resolveDuplicate(ProjectMember member, DataIntegrityViolationException ex) {
        if (mysqlDuplicateKeyDetector.isNotDuplicateKey(ex)) {
            return null;
        }

        ProjectMember existing = queryFactory.selectFrom(projectMember)
                                             .where(
                                                     projectMember.teamId.eq(member.getTeamId()),
                                                     projectMember.slackUserId.eq(member.getSlackUserId())
                                             )
                                             .fetchOne();
        if (existing == null) {
            return null;
        }
        existing.connectGithubId(member.getGithubId());
        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectMember> findBySlackUser(String teamId, String slackUserId) {
        ProjectMember result = queryFactory.selectFrom(projectMember)
                                           .where(
                                                   projectMember.teamId.eq(teamId),
                                                   projectMember.slackUserId.eq(slackUserId)
                                           )
                                           .fetchOne();

        return Optional.ofNullable(result);
    }
}
