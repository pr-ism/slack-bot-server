package com.slack.bot.infrastructure.interaction.persistence.box.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackInteractionInboxRepository extends ListCrudRepository<SlackInteractionInbox, Long> {
}
