package com.slack.bot.infrastructure.analysis.metadata.reservation.persistence;

import static com.slack.bot.domain.analysis.metadata.reservation.QReviewReservationInteraction.reviewReservationInteraction;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewReservationInteractionRepositoryAdapter implements ReviewReservationInteractionRepository {

    private final JPAQueryFactory queryFactory;
    private final ReviewReservationInteractionCreator reviewReservationInteractionCreator;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;

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
    public void recordReviewTimeSelected(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewTimeSelectedAt
    ) {
        if (updateReviewTimeSelectedAt(teamId, projectId, pullRequestId, reviewerSlackId, reviewTimeSelectedAt)) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId
        );
        interaction.recordReviewTimeSelectedAt(reviewTimeSelectedAt);

        try {
            reviewReservationInteractionCreator.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            updateReviewTimeSelectedAt(
                    teamId,
                    projectId,
                    pullRequestId,
                    reviewerSlackId,
                    reviewTimeSelectedAt
            );
        }
    }

    @Override
    @Transactional
    public void recordScheduleChanged(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        if (increaseScheduleChangeCount(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId
        );
        interaction.increaseScheduleChangeCount();

        try {
            reviewReservationInteractionCreator.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            increaseScheduleChangeCount(
                    teamId,
                    projectId,
                    pullRequestId,
                    reviewerSlackId
            );
        }
    }

    @Override
    @Transactional
    public void recordScheduleCanceled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        if (increaseScheduleCancelCount(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId
        );
        interaction.increaseScheduleCancelCount();

        try {
            reviewReservationInteractionCreator.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            increaseScheduleCancelCount(
                    teamId,
                    projectId,
                    pullRequestId,
                    reviewerSlackId
            );
        }
    }

    @Override
    @Transactional
    public void recordReviewScheduled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewScheduledAt,
            Instant pullRequestNotifiedAt
    ) {
        if (updateReviewScheduledAtAndPullRequestNotifiedAt(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId,
                reviewScheduledAt,
                pullRequestNotifiedAt
        )) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId
        );
        interaction.recordReviewScheduledAt(reviewScheduledAt);
        interaction.recordPullRequestNotifiedAt(pullRequestNotifiedAt);

        try {
            reviewReservationInteractionCreator.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            updateReviewScheduledAtAndPullRequestNotifiedAt(
                    teamId,
                    projectId,
                    pullRequestId,
                    reviewerSlackId,
                    reviewScheduledAt,
                    pullRequestNotifiedAt
            );
        }
    }

    @Override
    @Transactional
    public void recordReviewFulfilled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant pullRequestNotifiedAt
    ) {
        if (markReviewFulfilledAndUpdatePullRequestNotifiedAt(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId,
                pullRequestNotifiedAt
        )) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId
        );
        interaction.markReviewFulfilled();
        interaction.recordPullRequestNotifiedAt(pullRequestNotifiedAt);

        try {
            reviewReservationInteractionCreator.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            markReviewFulfilledAndUpdatePullRequestNotifiedAt(
                    teamId,
                    projectId,
                    pullRequestId,
                    reviewerSlackId,
                    pullRequestNotifiedAt
            );
        }
    }

    private boolean updateReviewTimeSelectedAt(
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

    private boolean increaseScheduleChangeCount(
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

    private boolean increaseScheduleCancelCount(
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

    private boolean updateReviewScheduledAtAndPullRequestNotifiedAt(
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

    private boolean markReviewFulfilledAndUpdatePullRequestNotifiedAt(
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
