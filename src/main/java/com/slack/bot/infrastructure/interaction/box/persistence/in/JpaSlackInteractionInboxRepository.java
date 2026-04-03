package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackInteractionInboxRepository extends ListCrudRepository<SlackInteractionInbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT inbox FROM SlackInteractionInbox inbox WHERE inbox.id = :id")
    Optional<SlackInteractionInbox> findLockedById(Long id);
}
