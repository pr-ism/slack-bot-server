package com.slack.bot.infrastructure.link.persistence;

import static com.slack.bot.domain.link.QAccessLinkSequence.accessLinkSequence;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.link.AccessLinkSequence;
import com.slack.bot.infrastructure.link.persistence.exception.AccessLinkSequenceStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AccessLinkSequencePersistenceHandler {

    private final JPAQueryFactory queryFactory;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long tryIncrement(Long size) {
        long updatedRows = queryFactory.update(accessLinkSequence)
                                       .set(accessLinkSequence.nextValue, accessLinkSequence.nextValue.add(size))
                                       .where(accessLinkSequence.id.eq(AccessLinkSequence.DEFAULT_ID))
                                       .execute();

        if (updatedRows == 0L) {
            throw new AccessLinkSequenceStateException();
        }

        Long current = queryFactory.select(accessLinkSequence.nextValue)
                                   .from(accessLinkSequence)
                                   .where(accessLinkSequence.id.eq(AccessLinkSequence.DEFAULT_ID))
                                   .fetchOne();

        if (current == null) {
            throw new AccessLinkSequenceStateException();
        }

        return current;
    }
}
