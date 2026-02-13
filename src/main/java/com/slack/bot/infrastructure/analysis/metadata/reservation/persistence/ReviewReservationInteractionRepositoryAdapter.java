package com.slack.bot.infrastructure.analysis.metadata.reservation.persistence;

import static com.slack.bot.domain.analysis.metadata.reservation.QReviewReservationInteraction.reviewReservationInteraction;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewReservationInteractionRepositoryAdapter implements ReviewReservationInteractionRepository {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public ReviewReservationInteraction create(ReviewReservationInteraction interaction) {
        entityManager.persist(interaction);
        entityManager.flush();
        return interaction;
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

    @Override
    @Transactional
    public boolean updateReviewTimeSelectedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewTimeSelectedAt
    ) {
        long updatedCount = queryFactory
                .update(reviewReservationInteraction)
                .set(reviewReservationInteraction.interactionTimeline.reviewTimeSelectedAt, reviewTimeSelectedAt)
                .where(
                        reviewReservationInteraction.teamId.eq(teamId),
                        reviewReservationInteraction.projectId.eq(projectId),
                        reviewReservationInteraction.pullRequestId.eq(pullRequestId),
                        reviewReservationInteraction.reviewerSlackId.eq(reviewerSlackId)
                )
                .execute();

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public boolean increaseScheduleChangeCount(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        long updatedCount = queryFactory
                .update(reviewReservationInteraction)
                .set(
                        reviewReservationInteraction.interactionCount.scheduleChangeCount,
                        reviewReservationInteraction.interactionCount.scheduleChangeCount.add(1)
                )
                .where(
                        reviewReservationInteraction.teamId.eq(teamId),
                        reviewReservationInteraction.projectId.eq(projectId),
                        reviewReservationInteraction.pullRequestId.eq(pullRequestId),
                        reviewReservationInteraction.reviewerSlackId.eq(reviewerSlackId)
                )
                .execute();

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public boolean increaseScheduleCancelCount(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        long updatedCount = queryFactory
                .update(reviewReservationInteraction)
                .set(
                        reviewReservationInteraction.interactionCount.scheduleCancelCount,
                        reviewReservationInteraction.interactionCount.scheduleCancelCount.add(1)
                )
                .where(
                        reviewReservationInteraction.teamId.eq(teamId),
                        reviewReservationInteraction.projectId.eq(projectId),
                        reviewReservationInteraction.pullRequestId.eq(pullRequestId),
                        reviewReservationInteraction.reviewerSlackId.eq(reviewerSlackId)
                )
                .execute();

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public boolean updateReviewScheduledAtAndPullRequestNotifiedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewScheduledAt,
            Instant pullRequestNotifiedAt
    ) {
        long updatedCount = queryFactory
                .update(reviewReservationInteraction)
                .set(reviewReservationInteraction.interactionTimeline.reviewScheduledAt, reviewScheduledAt)
                .set(reviewReservationInteraction.interactionTimeline.pullRequestNotifiedAt, pullRequestNotifiedAt)
                .where(
                        reviewReservationInteraction.teamId.eq(teamId),
                        reviewReservationInteraction.projectId.eq(projectId),
                        reviewReservationInteraction.pullRequestId.eq(pullRequestId),
                        reviewReservationInteraction.reviewerSlackId.eq(reviewerSlackId)
                )
                .execute();

        return updatedCount > 0;
    }

    @Override
    @Transactional
    public boolean markReviewFulfilledAndUpdatePullRequestNotifiedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant pullRequestNotifiedAt
    ) {
        long updatedCount = queryFactory
                .update(reviewReservationInteraction)
                .set(reviewReservationInteraction.reviewFulfilled, true)
                .set(reviewReservationInteraction.interactionTimeline.pullRequestNotifiedAt, pullRequestNotifiedAt)
                .where(
                        reviewReservationInteraction.teamId.eq(teamId),
                        reviewReservationInteraction.projectId.eq(projectId),
                        reviewReservationInteraction.pullRequestId.eq(pullRequestId),
                        reviewReservationInteraction.reviewerSlackId.eq(reviewerSlackId)
                )
                .execute();

        return updatedCount > 0;
    }
}
