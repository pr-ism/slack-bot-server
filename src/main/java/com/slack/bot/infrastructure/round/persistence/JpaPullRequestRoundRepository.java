package com.slack.bot.infrastructure.round.persistence;

import com.slack.bot.domain.round.PullRequestRound;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaPullRequestRoundRepository extends ListCrudRepository<PullRequestRound, Long> {
}
