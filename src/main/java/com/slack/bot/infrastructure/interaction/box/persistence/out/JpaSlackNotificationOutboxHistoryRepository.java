package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackNotificationOutboxHistoryRepository
        extends ListCrudRepository<SlackNotificationOutboxHistoryJpaEntity, Long> {

    default List<SlackNotificationOutboxHistory> findAllDomains() {
        return findAll().stream()
                        .map(history -> history.toDomain())
                        .toList();
    }
}
