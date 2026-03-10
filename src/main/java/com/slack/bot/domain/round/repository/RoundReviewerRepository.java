package com.slack.bot.domain.round.repository;

import com.slack.bot.domain.round.RoundReviewer;
import java.util.List;
import java.util.Optional;

public interface RoundReviewerRepository {

    Optional<RoundReviewer> findReviewerInRound(Long pullRequestRoundId, String reviewerGithubId);

    List<RoundReviewer> findAllInRound(Long pullRequestRoundId);

    RoundReviewer save(RoundReviewer roundReviewer);

    List<RoundReviewer> findAll();
}
