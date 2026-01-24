package com.slack.bot.infrastructure.link.persistence;

import com.slack.bot.domain.link.AccessLinkSequence;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AccessLinkSequenceCreator {

    private final JpaAccessLinkSequenceRepository sequenceRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initializeSequenceIfAbsent(Long initialValue) {
        AccessLinkSequence accessLinkSequence = AccessLinkSequence.create(AccessLinkSequence.DEFAULT_ID, initialValue);

        sequenceRepository.save(accessLinkSequence);
        entityManager.flush();
    }
}
