package com.slack.bot.infrastructure.round.persistence;

import com.slack.bot.domain.round.RoundReviewer;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaRoundReviewerRepository extends ListCrudRepository<RoundReviewer, Long> {
}
