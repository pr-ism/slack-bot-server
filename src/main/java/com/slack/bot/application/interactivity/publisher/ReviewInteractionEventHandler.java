package com.slack.bot.application.interactivity.publisher;

import com.slack.bot.domain.analysis.metadata.reservation.repository.ReviewReservationInteractionRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Async("reviewInteractionExecutor")
@RequiredArgsConstructor
public class ReviewInteractionEventHandler {

    private final Clock clock;
    private final ReviewReservationInteractionRepository reviewReservationInteractionRepository;

    @EventListener
    @Transactional
    public void handleReviewReservationRequestEvent(ReviewReservationRequestEvent event) {
        recordReviewTimeSelected(event.teamId(), event.projectId(), event.pullRequestId(), event.slackUserId());
    }

    @EventListener
    @Transactional
    public void handleReviewReservationChangeEvent(ReviewReservationChangeEvent event) {
        recordScheduleChanged(event.teamId(), event.projectId(), event.pullRequestId(), event.slackUserId());
    }

    @EventListener
    @Transactional
    public void handleReviewReservationCancelEvent(ReviewReservationCancelEvent event) {
        recordScheduleCanceled(event.teamId(), event.projectId(), event.pullRequestId(), event.slackUserId());
    }

    @EventListener
    @Transactional
    public void handleReviewReservationScheduledEvent(ReviewReservationScheduledEvent event) {
        Instant pullRequestNotifiedAt = resolvePullRequestNotifiedAt(event.pullRequestNotifiedAt());

        recordReviewScheduled(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                event.reviewScheduledAt(),
                pullRequestNotifiedAt
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationFulfilledEvent(ReviewReservationFulfilledEvent event) {
        Instant pullRequestNotifiedAt = resolvePullRequestNotifiedAt(event.pullRequestNotifiedAt());

        recordReviewFulfilled(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                pullRequestNotifiedAt
        );
    }

    private void recordReviewTimeSelected(String teamId, Long projectId, Long pullRequestId, String reviewerSlackId) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        Instant reviewTimeSelectedAt = clock.instant();
        reviewReservationInteractionRepository.recordReviewTimeSelected(
                teamId, projectId, pullRequestId, reviewerSlackId, reviewTimeSelectedAt
        );
    }

    private void recordScheduleChanged(String teamId, Long projectId, Long pullRequestId, String reviewerSlackId) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        reviewReservationInteractionRepository.recordScheduleChanged(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId
        );
    }

    private void recordScheduleCanceled(String teamId, Long projectId, Long pullRequestId, String reviewerSlackId) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        reviewReservationInteractionRepository.recordScheduleCanceled(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId
        );
    }

    private void recordReviewScheduled(
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

        reviewReservationInteractionRepository.recordReviewScheduled(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId,
                reviewScheduledAt,
                pullRequestNotifiedAt
        );
    }

    private void recordReviewFulfilled(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Instant pullRequestNotifiedAt
    ) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        reviewReservationInteractionRepository.recordReviewFulfilled(
                teamId,
                projectId,
                pullRequestId,
                reviewerSlackId,
                pullRequestNotifiedAt
        );
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
