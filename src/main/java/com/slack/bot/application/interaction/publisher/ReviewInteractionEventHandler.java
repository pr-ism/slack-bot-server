package com.slack.bot.application.interaction.publisher;

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
        recordReviewTimeSelected(event.teamId(), event.projectId(), event.githubPullRequestId(), event.slackUserId());
    }

    @EventListener
    @Transactional
    public void handleReviewReservationChangeEvent(ReviewReservationChangeEvent event) {
        recordScheduleChanged(event.teamId(), event.projectId(), event.githubPullRequestId(), event.slackUserId());
    }

    @EventListener
    @Transactional
    public void handleReviewReservationCancelEvent(ReviewReservationCancelEvent event) {
        recordScheduleCanceled(event.teamId(), event.projectId(), event.githubPullRequestId(), event.slackUserId());
    }

    @EventListener
    @Transactional
    public void handleReviewReservationScheduledEvent(ReviewReservationScheduledEvent event) {
        Instant pullRequestNotifiedAt = resolvePullRequestNotifiedAt(event.pullRequestNotifiedAt());

        recordReviewScheduled(
                event.teamId(),
                event.projectId(),
                event.githubPullRequestId(),
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
                event.githubPullRequestId(),
                event.slackUserId(),
                pullRequestNotifiedAt
        );
    }

    private void recordReviewTimeSelected(String teamId, Long projectId, Long githubPullRequestId, String reviewerSlackId) {
        if (isInvalidReviewKey(teamId, projectId, githubPullRequestId, reviewerSlackId)) {
            return;
        }

        Instant reviewTimeSelectedAt = clock.instant();
        reviewReservationInteractionRepository.recordReviewTimeSelected(
                teamId, projectId, githubPullRequestId, reviewerSlackId, reviewTimeSelectedAt
        );
    }

    private void recordScheduleChanged(String teamId, Long projectId, Long githubPullRequestId, String reviewerSlackId) {
        if (isInvalidReviewKey(teamId, projectId, githubPullRequestId, reviewerSlackId)) {
            return;
        }

        reviewReservationInteractionRepository.recordScheduleChanged(
                teamId,
                projectId,
                githubPullRequestId,
                reviewerSlackId
        );
    }

    private void recordScheduleCanceled(String teamId, Long projectId, Long githubPullRequestId, String reviewerSlackId) {
        if (isInvalidReviewKey(teamId, projectId, githubPullRequestId, reviewerSlackId)) {
            return;
        }

        reviewReservationInteractionRepository.recordScheduleCanceled(
                teamId,
                projectId,
                githubPullRequestId,
                reviewerSlackId
        );
    }

    private void recordReviewScheduled(
            String teamId,
            Long projectId,
            Long githubPullRequestId,
            String reviewerSlackId,
            Instant reviewScheduledAt,
            Instant pullRequestNotifiedAt
    ) {
        if (isInvalidReviewKey(teamId, projectId, githubPullRequestId, reviewerSlackId)) {
            return;
        }

        reviewReservationInteractionRepository.recordReviewScheduled(
                teamId,
                projectId,
                githubPullRequestId,
                reviewerSlackId,
                reviewScheduledAt,
                pullRequestNotifiedAt
        );
    }

    private void recordReviewFulfilled(
            String teamId,
            Long projectId,
            Long githubPullRequestId,
            String reviewerSlackId,
            Instant pullRequestNotifiedAt
    ) {
        if (isInvalidReviewKey(teamId, projectId, githubPullRequestId, reviewerSlackId)) {
            return;
        }

        reviewReservationInteractionRepository.recordReviewFulfilled(
                teamId,
                projectId,
                githubPullRequestId,
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
            Long githubPullRequestId,
            String reviewerSlackId
    ) {
        if (teamId == null || teamId.isBlank()) {
            return true;
        }
        if (projectId == null) {
            return true;
        }
        if (githubPullRequestId == null) {
            return true;
        }

        return reviewerSlackId == null || reviewerSlackId.isBlank();
    }
}
