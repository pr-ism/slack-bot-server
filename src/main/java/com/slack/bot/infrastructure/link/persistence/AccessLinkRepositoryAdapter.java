package com.slack.bot.infrastructure.link.persistence;

import static com.slack.bot.domain.link.QAccessLink.accessLink;
import static com.slack.bot.domain.member.QProjectMember.projectMember;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.link.AccessLink;
import com.slack.bot.domain.link.repository.AccessLinkRepository;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.infrastructure.link.persistence.exception.AccessLinkDuplicateKeyException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.SQLException;

@Repository
@RequiredArgsConstructor
public class AccessLinkRepositoryAdapter implements AccessLinkRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaAccessLinkRepository accessLinkRepository;
    private final AccessLinkPersistenceHandler persistenceHandler;

    @Override
    @Transactional(readOnly = true)
    public AccessLink saveOrFindExisting(AccessLink link) {
        try {
            return persistenceHandler.save(link);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateKey(ex)) {
                return accessLinkRepository.findByProjectMemberId(link.getProjectMemberId())
                                           .orElseThrow(() -> new AccessLinkDuplicateKeyException());
            }

            throw ex;
        }
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

    private boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof ConstraintViolationException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();

                // MySQL 무결성 제약 조건 코드
                if ("23000".equals(sqlState) && sqlException.getErrorCode() == 1062) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
