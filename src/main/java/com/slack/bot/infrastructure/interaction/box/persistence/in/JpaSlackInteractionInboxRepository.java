package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackInteractionInboxRepository extends ListCrudRepository<SlackInteractionInboxJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT inbox FROM SlackInteractionInboxJpaEntity inbox WHERE inbox.id = :id")
    Optional<SlackInteractionInboxJpaEntity> findLockedById(Long id);

    default Optional<SlackInteractionInbox> findDomainById(Long id) {
        return findById(id).map(inbox -> inbox.toDomain());
    }

    default List<SlackInteractionInbox> findAllDomains() {
        return findAll().stream()
                        .map(inbox -> inbox.toDomain())
                        .toList();
    }
}
