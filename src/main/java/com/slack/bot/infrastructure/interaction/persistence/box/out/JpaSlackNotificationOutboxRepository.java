package com.slack.bot.infrastructure.interaction.persistence.box.out;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackNotificationOutboxRepository extends ListCrudRepository<SlackNotificationOutbox, Long> {
}
