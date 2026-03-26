package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackInteractionInboxHistoryRepository
        extends ListCrudRepository<SlackInteractionInboxHistory, Long> {
}
