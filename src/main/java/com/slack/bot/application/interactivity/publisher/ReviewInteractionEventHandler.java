package com.slack.bot.application.interactivity.publisher;

import com.slack.bot.domain.reservation.ReviewReservationInteraction;
import com.slack.bot.domain.reservation.repository.ReviewReservationInteractionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;
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
        updateInteraction(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                interaction -> interaction.recordReviewTimeSelectedAt(clock.instant())
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationChangeEvent(ReviewReservationChangeEvent event) {
        updateInteraction(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                ReviewReservationInteraction::increaseScheduleChangeCount
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationCancelEvent(ReviewReservationCancelEvent event) {
        updateInteraction(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                ReviewReservationInteraction::increaseScheduleCancelCount
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationScheduledEvent(ReviewReservationScheduledEvent event) {
        updateInteraction(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                interaction -> {
                    interaction.recordReviewScheduledAt(event.reviewScheduledAt());
                    interaction.recordPullRequestNotifiedAt(
                            resolvePullRequestNotifiedAt(event.pullRequestNotifiedAt())
                    );
                }
        );
    }

    @EventListener
    @Transactional
    public void handleReviewReservationFulfilledEvent(ReviewReservationFulfilledEvent event) {
        updateInteraction(
                event.teamId(),
                event.projectId(),
                event.pullRequestId(),
                event.slackUserId(),
                interaction -> {
                    interaction.markReviewFulfilled();
                    interaction.recordPullRequestNotifiedAt(
                            resolvePullRequestNotifiedAt(event.pullRequestNotifiedAt())
                    );
                }
        );
    }

    private void updateInteraction(
            String teamId,
            Long projectId,
            Long pullRequestId,
            String reviewerSlackId,
            Consumer<ReviewReservationInteraction> updater
    ) {
        if (isInvalidReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)) {
            return;
        }

        ReviewReservationInteraction interaction = reviewReservationInteractionRepository
                .findByReviewKey(teamId, projectId, pullRequestId, reviewerSlackId)
                .orElseGet(() -> ReviewReservationInteraction.create(
                        teamId,
                        projectId,
                        pullRequestId,
                        reviewerSlackId
                ));
        updater.accept(interaction);
        reviewReservationInteractionRepository.save(interaction);
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
