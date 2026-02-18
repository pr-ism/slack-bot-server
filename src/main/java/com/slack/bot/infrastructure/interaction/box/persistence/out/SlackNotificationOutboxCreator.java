package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SlackNotificationOutboxCreator {

    private final EntityManager entityManager;
    private final JpaSlackNotificationOutboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNew(SlackNotificationOutbox outbox) {
        repository.save(outbox);
        entityManager.flush();
    }
}
