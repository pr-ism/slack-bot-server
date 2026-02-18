package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SlackInteractionInboxCreator {

    private final EntityManager entityManager;
    private final JpaSlackInteractionInboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNew(SlackInteractionInbox inbox) {
        repository.save(inbox);
        entityManager.flush();
    }
}
