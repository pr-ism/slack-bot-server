package com.slack.bot.application.interactivity.publisher;

import com.slack.bot.domain.analysis.metadata.reservation.ReviewReservationInteraction;
import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Async("reviewInteractionExecutor")
@RequiredArgsConstructor
public class ReviewInteractionEventHandler {

    private final Clock clock;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;
    private final ReviewReservationInteractionRepository reviewReservationInteractionRepository;

    @EventListener
    @Transactional
    public void handleReviewReservationRequestEvent(ReviewReservationRequestEvent event) {
        updateOrCreateReviewTimeSelectedAt(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId()
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationChangeEvent(ReviewReservationChangeEvent event) {
        updateOrCreateScheduleChangeCount(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId()
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationCancelEvent(ReviewReservationCancelEvent event) {
        updateOrCreateScheduleCancelCount(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId()
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationScheduledEvent(ReviewReservationScheduledEvent event) {
        updateOrCreateReviewScheduledAtAndPullRequestNotifiedAt(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                event.reviewScheduledAt(),
                resolvePullRequestNotifiedAt(event.pullRequestNotifiedAt())
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationFulfilledEvent(ReviewReservationFulfilledEvent event) {
        markOrCreateReviewFulfilled(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                resolvePullRequestNotifiedAt(event.pullRequestNotifiedAt())
        );
    }

    private void updateOrCreateReviewTimeSelectedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        Instant reviewTimeSelectedAt = clock.instant();
        if (reviewReservationInteractionRepository.updateReviewTimeSelectedAt(
                teamId, projectId, pullRequestId, reviewerSlackId, reviewTimeSelectedAt
        )) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId, projectId, pullRequestId, reviewerSlackId
        );
        interaction.recordReviewTimeSelectedAt(reviewTimeSelectedAt);

        try {
            reviewReservationInteractionRepository.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            reviewReservationInteractionRepository.updateReviewTimeSelectedAt(
                    teamId, projectId, pullRequestId, reviewerSlackId, reviewTimeSelectedAt
            );
        }
    }

    private void updateOrCreateScheduleChangeCount(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        if (reviewReservationInteractionRepository.increaseScheduleChangeCount(
                teamId, projectId, pullRequestId, reviewerSlackId
        )) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId, projectId, pullRequestId, reviewerSlackId
        );
        interaction.increaseScheduleChangeCount();

        try {
            reviewReservationInteractionRepository.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            reviewReservationInteractionRepository.increaseScheduleChangeCount(
                    teamId, projectId, pullRequestId, reviewerSlackId
            );
        }
    }

    private void updateOrCreateScheduleCancelCount(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        if (reviewReservationInteractionRepository.increaseScheduleCancelCount(
                teamId, projectId, pullRequestId, reviewerSlackId
        )) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId, projectId, pullRequestId, reviewerSlackId
        );
        interaction.increaseScheduleCancelCount();

        try {
            reviewReservationInteractionRepository.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            reviewReservationInteractionRepository.increaseScheduleCancelCount(
                    teamId, projectId, pullRequestId, reviewerSlackId
            );
        }
    }

    private void updateOrCreateReviewScheduledAtAndPullRequestNotifiedAt(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant reviewScheduledAt,
            Instant pullRequestNotifiedAt
    ) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        if (reviewReservationInteractionRepository.updateReviewScheduledAtAndPullRequestNotifiedAt(
                teamId, projectId, pullRequestId, reviewerSlackId, reviewScheduledAt, pullRequestNotifiedAt
        )) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId, projectId, pullRequestId, reviewerSlackId
        );
        interaction.recordReviewScheduledAt(reviewScheduledAt);
        interaction.recordPullRequestNotifiedAt(pullRequestNotifiedAt);

        try {
            reviewReservationInteractionRepository.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            reviewReservationInteractionRepository.updateReviewScheduledAtAndPullRequestNotifiedAt(
                    teamId,
                    projectId,
                    pullRequestId,
                    reviewerSlackId,
                    reviewScheduledAt,
                    pullRequestNotifiedAt
            );
        }
    }

    private void markOrCreateReviewFulfilled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant pullRequestNotifiedAt
    ) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        if (reviewReservationInteractionRepository.markReviewFulfilledAndUpdatePullRequestNotifiedAt(
                teamId, projectId, pullRequestId, reviewerSlackId, pullRequestNotifiedAt
        )) {
            return;
        }

        ReviewReservationInteraction interaction = ReviewReservationInteraction.create(
                teamId, projectId, pullRequestId, reviewerSlackId
        );
        interaction.markReviewFulfilled();
        interaction.recordPullRequestNotifiedAt(pullRequestNotifiedAt);

        try {
            reviewReservationInteractionRepository.create(interaction);
        } catch (DataIntegrityViolationException exception) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(exception)) {
                throw exception;
            }
            reviewReservationInteractionRepository.markReviewFulfilledAndUpdatePullRequestNotifiedAt(
                    teamId, projectId, pullRequestId, reviewerSlackId, pullRequestNotifiedAt
            );
        }
    }

    private Instant resolvePullRequestNotifiedAt(Instant pullRequestNotifiedAt) {
        if (pullRequestNotifiedAt != null) {
            return pullRequestNotifiedAt;
        }

        return clock.instant();
    }

    private boolean isInvalidReviewKey(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId
    ) {
        if (teamId == null || teamId.isBlank()) {
            return true;
        }
        if (projectId == null) {
            return true;
        }
        if (pullRequestId == null) {
            return true;
        }
        return reviewerSlackId == null || reviewerSlackId.isBlank();
    }
}
