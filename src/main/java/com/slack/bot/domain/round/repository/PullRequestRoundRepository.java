package com.slack.bot.domain.round.repository;

import com.slack.bot.domain.round.PullRequestRound;
import java.util.List;
import java.util.Optional;

public interface PullRequestRoundRepository {

    Optional<PullRequestRound> findLatestRound(
            String apiKey,
            Long githubPullRequestId
    );

    Optional<PullRequestRound> findRoundByStartCommitHash(
            String apiKey,
            Long githubPullRequestId,
            String startCommitHash
    );

    PullRequestRound save(PullRequestRound pullRequestRound);

    List<PullRequestRound> findAll();
}
