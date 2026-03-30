package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackNotificationOutboxHistoryRepository
        extends ListCrudRepository<SlackNotificationOutboxHistory, Long> {
}
