package com.slack.bot.infrastructure.link.persistence;

import static com.slack.bot.domain.link.QAccessLinkSequence.accessLinkSequence;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.link.AccessLinkSequence;
import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;
import com.slack.bot.domain.link.repository.AccessLinkSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccessLinkSequenceRepositoryAdapter implements AccessLinkSequenceRepository {

    private static final Object CREATE_LOCK = new Object();

    private final JPAQueryFactory queryFactory;
    private final AccessLinkSequencePersistenceHandler persistenceHandler;
    private final AccessLinkSequenceCreator accessLinkSequenceCreator;

    @Override
    public AccessLinkSequenceBlockDto allocateBlock(Long size, Long initialValue) {
        if (size == null || size <= 0L) {
            throw new IllegalArgumentException("블록 크기는 0보다 커야 합니다.");
        }

        long updatedNextValue = incrementNextValue(size, initialValue);
        long start = updatedNextValue - size + 1L;

        return new AccessLinkSequenceBlockDto(start, updatedNextValue);
    }

    private long incrementNextValue(Long size, Long initialValue) {
        try {
            return persistenceHandler.tryIncrement(size);
        } catch (DataAccessException ex) {
            ensureInitialized(initialValue);

            return persistenceHandler.tryIncrement(size);
        }
    }

    private void ensureInitialized(Long initialValue) {
        synchronized (CREATE_LOCK) {
            boolean exists = queryFactory.selectOne()
                                         .from(accessLinkSequence)
                                         .where(accessLinkSequence.id.eq(AccessLinkSequence.DEFAULT_ID))
                                         .fetchFirst() != null;
            if (exists) {
                return;
            }

            createIfAbsent(initialValue);
        }
    }

    private void createIfAbsent(Long initialValue) {
        try {
            accessLinkSequenceCreator.initializeSequenceIfAbsent(initialValue);
        } catch (DataIntegrityViolationException ignored) {
            // 다른 트랜잭션에서 이미 생성한 경우 무시
        }
    }
}
