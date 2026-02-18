package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaSlackInteractionInboxRepository extends ListCrudRepository<SlackInteractionInbox, Long> {
}
