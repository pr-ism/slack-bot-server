package com.slack.bot.infrastructure.link.persistence;

import com.slack.bot.domain.link.AccessLinkSequence;
import org.springframework.data.repository.CrudRepository;

public interface JpaAccessLinkSequenceRepository extends CrudRepository<AccessLinkSequence, Long> {
}
