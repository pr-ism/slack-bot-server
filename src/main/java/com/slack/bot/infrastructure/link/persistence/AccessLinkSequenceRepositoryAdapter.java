package com.slack.bot.infrastructure.link.persistence;

import static com.slack.bot.domain.link.QAccessLinkSequence.accessLinkSequence;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.link.AccessLinkSequence;
import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;
import com.slack.bot.domain.link.repository.AccessLinkSequenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AccessLinkSequenceRepositoryAdapter implements AccessLinkSequenceRepository {

    private final JpaAccessLinkSequenceRepository sequenceRepository;
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public AccessLinkSequenceBlockDto allocateBlock(Long size, Long initialValue) {
        AccessLinkSequence sequence = getOrCreate(initialValue);

        return sequence.allocateBlock(size);
    }

    private AccessLinkSequence getOrCreate(Long initialValue) {
        AccessLinkSequence locked = queryFactory.selectFrom(accessLinkSequence)
                                                .where(accessLinkSequence.id.eq(AccessLinkSequence.DEFAULT_ID))
                                                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                                                .fetchOne();
        if (locked != null) {
            return locked;
        }
        return createAndLock(initialValue);
    }

    private AccessLinkSequence createAndLock(Long initialValue) {
        try {
            sequenceRepository.save(AccessLinkSequence.create(AccessLinkSequence.DEFAULT_ID, initialValue));
            entityManager.flush();
        } catch (DataIntegrityViolationException ignored) {
            // 다른 트랜잭션이 먼저 생성한 경우 무시
        }

        AccessLinkSequence locked = queryFactory.selectFrom(accessLinkSequence)
                                                .where(accessLinkSequence.id.eq(AccessLinkSequence.DEFAULT_ID))
                                                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                                                .fetchOne();
        if (locked == null) {
            throw new IllegalStateException("AccessLink 시퀀스를 초기화할 수 없습니다.");
        }

        return locked;
    }
}
