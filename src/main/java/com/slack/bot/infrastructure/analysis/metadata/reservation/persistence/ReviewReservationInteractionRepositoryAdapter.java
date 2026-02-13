package com.slack.bot.infrastructure.analysis.metadata.reservation.persistence;

import static com.slack.bot.domain.analysis.metadata.reservation.QReviewReservationInteraction.reviewReservationInteraction;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewReservationInteractionRepositoryAdapter implements ReviewReservationInteractionRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaReviewReservationInteractionRepository jpaReviewReservationInteractionRepository;

    @Override
    @Transactional
    public ReviewReservationInteraction save(ReviewReservationInteraction interaction) {
        return jpaReviewReservationInteractionRepository.save(interaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewReservationInteraction> findByReviewKey(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        ReviewReservationInteraction found = queryFactory
                .selectFrom(reviewReservationInteraction)
                .where(
                        reviewReservationInteraction.teamId.eq(teamId),
                        reviewReservationInteraction.projectId.eq(projectId),
                        reviewReservationInteraction.pullRequestId.eq(pullRequestId),
                        reviewReservationInteraction.reviewerSlackId.eq(reviewerSlackId)
                )
                .fetchOne();

        return Optional.ofNullable(found);
    }
}
