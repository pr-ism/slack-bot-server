package com.slack.bot.infrastructure.link.persistence;

import com.slack.bot.domain.link.AccessLink;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AccessLinkPersistenceHandler {

    private final EntityManager entityManager;
    private final JpaAccessLinkRepository accessLinkRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccessLink save(AccessLink link) {
        AccessLink savedLink = accessLinkRepository.save(link);

        entityManager.flush();
        return savedLink;
    }
}
