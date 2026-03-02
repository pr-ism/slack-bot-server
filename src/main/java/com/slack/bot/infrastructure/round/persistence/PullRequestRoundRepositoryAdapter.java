package com.slack.bot.infrastructure.round.persistence;

import static com.slack.bot.domain.round.QPullRequestRound.pullRequestRound;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.round.PullRequestRound;
import com.slack.bot.domain.round.repository.PullRequestRoundRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PullRequestRoundRepositoryAdapter implements PullRequestRoundRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaPullRequestRoundRepository jpaPullRequestRoundRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PullRequestRound> findLatestRound(
            String apiKey,
            Long githubPullRequestId
    ) {
        PullRequestRound result = queryFactory.selectFrom(pullRequestRound)
                                              .where(
                                                      pullRequestRound.apiKey.eq(apiKey),
                                                      pullRequestRound.githubPullRequestId.eq(githubPullRequestId)
                                              )
                                              .orderBy(pullRequestRound.roundNumber.desc())
                                              .fetchFirst();

        return Optional.ofNullable(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PullRequestRound> findRoundByStartCommitHash(
            String apiKey,
            Long githubPullRequestId,
            String startCommitHash
    ) {
        PullRequestRound result = queryFactory.selectFrom(pullRequestRound)
                                              .where(
                                                      pullRequestRound.apiKey.eq(apiKey),
                                                      pullRequestRound.githubPullRequestId.eq(githubPullRequestId),
                                                      pullRequestRound.startCommitHash.eq(startCommitHash)
                                              )
                                              .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    @Transactional
    public PullRequestRound save(PullRequestRound pullRequestRound) {
        return jpaPullRequestRoundRepository.save(pullRequestRound);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PullRequestRound> findAll() {
        return jpaPullRequestRoundRepository.findAll();
    }
}
