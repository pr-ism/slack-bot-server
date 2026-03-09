package com.slack.bot.infrastructure.round.persistence;

import static com.slack.bot.domain.round.QRoundReviewer.roundReviewer;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.round.RoundReviewer;
import com.slack.bot.domain.round.repository.RoundReviewerRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class RoundReviewerRepositoryAdapter implements RoundReviewerRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaRoundReviewerRepository jpaRoundReviewerRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<RoundReviewer> findReviewerInRound(Long pullRequestRoundId, String reviewerGithubId) {
        RoundReviewer result = queryFactory.selectFrom(roundReviewer)
                                           .where(
                                                   roundReviewer.pullRequestRoundId.eq(pullRequestRoundId),
                                                   roundReviewer.reviewerGithubId.eq(reviewerGithubId)
                                           )
                                           .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoundReviewer> findAllInRound(Long pullRequestRoundId) {
        return queryFactory.selectFrom(roundReviewer)
                           .where(roundReviewer.pullRequestRoundId.eq(pullRequestRoundId))
                           .fetch();
    }

    @Override
    @Transactional
    public RoundReviewer save(RoundReviewer roundReviewer) {
        return jpaRoundReviewerRepository.save(roundReviewer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoundReviewer> findAll() {
        return jpaRoundReviewerRepository.findAll();
    }
}
