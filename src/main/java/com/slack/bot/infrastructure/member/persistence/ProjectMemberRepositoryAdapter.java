package com.slack.bot.infrastructure.member.persistence;

import static com.slack.bot.domain.member.QProjectMember.projectMember;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectMemberRepositoryAdapter implements ProjectMemberRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaProjectMemberRepository projectMemberRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public ProjectMember save(ProjectMember member) {
        try {
            projectMemberRepository.save(member);
            entityManager.flush();
            return member;
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateKey(ex)) {
                ProjectMember existing = queryFactory.selectFrom(projectMember)
                                                     .where(
                                                             projectMember.teamId.eq(member.getTeamId()),
                                                             projectMember.slackUserId.eq(member.getSlackUserId())
                                                     )
                                                     .fetchOne();
                if (existing != null) {
                    existing.connectGithubId(member.getGithubId());
                    return existing;
                }
            }
            throw ex;
        }
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

    private boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                // MySQL 무결성 제약 조건 코드
                if ("23505".equals(sqlState)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
