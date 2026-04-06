package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackInteractionInboxHistoryRepository
        extends ListCrudRepository<SlackInteractionInboxHistoryJpaEntity, Long> {

    default List<SlackInteractionInboxHistory> findAllDomains() {
        return findAll().stream()
                        .map(history -> history.toDomain())
                        .toList();
    }
}
