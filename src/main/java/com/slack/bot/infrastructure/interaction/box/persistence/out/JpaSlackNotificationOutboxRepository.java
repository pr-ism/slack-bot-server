package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackNotificationOutboxRepository extends ListCrudRepository<SlackNotificationOutboxJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT outbox FROM SlackNotificationOutboxJpaEntity outbox WHERE outbox.id = :id")
    Optional<SlackNotificationOutboxJpaEntity> findLockedById(Long id);

    default Optional<SlackNotificationOutbox> findDomainById(Long id) {
        return findById(id).map(outbox -> outbox.toDomain());
    }

    default List<SlackNotificationOutbox> findAllDomains() {
        return findAll().stream()
                        .map(outbox -> outbox.toDomain())
                        .toList();
    }
}
