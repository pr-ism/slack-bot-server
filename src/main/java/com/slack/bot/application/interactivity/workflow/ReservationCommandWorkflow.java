package com.slack.bot.application.interactivity.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.publisher.ReviewReservationCancelEvent;
import com.slack.bot.application.interactivity.publisher.ReviewReservationChangeEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.ReviewScheduleMetaBuilder;
import com.slack.bot.application.interactivity.view.factory.ReviewReservationTimeViewFactory;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationCommandWorkflow {

    private final NotificationApiClient slackApiClient;
    private final SlackActionErrorNotifier errorNotifier;
    private final ReviewReservationTimeViewFactory slackViews;
    private final ReviewScheduleMetaBuilder reviewScheduleMetaBuilder;
    private final Clock clock;
    private final ReviewReservationCoordinator reviewReservationCoordinator;
    private final ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    public Optional<ReviewReservation> handleCancel(
            JsonNode action,
            String teamId,
            String channelId,
            String slackUserId,
            String token
    ) {
        Long reservationId = readReservationId(action);

        if (isMissingReservationId(reservationId)) {
            return Optional.empty();
        }

        ReviewReservation reservation = findReservationOrNotify(reservationId, token, channelId, slackUserId);

        if (!isCancelableReservationOrNotify(reservation, token, channelId, slackUserId)) {
            return Optional.empty();
        }

        return cancelReservationOrFallback(teamId, channelId, slackUserId, reservationId, reservation);
    }

    public void handleChange(
            JsonNode payload,
            JsonNode action,
            String teamId,
            String channelId,
            String slackUserId,
            String token
    ) {
        Long reservationId = readReservationId(action);

        if (isMissingReservationId(reservationId)) {
            return;
        }

        ReviewReservation reservation = findReservationOrNotify(reservationId, token, channelId, slackUserId);

        if (!isChangeableReservationOrNotify(reservation, token, channelId, slackUserId)) {
            return;
        }

        publishChangeEvent(teamId, channelId, slackUserId, reservation);
        openChangeModal(payload, channelId, slackUserId, token, reservation);
    }

    private Long readReservationId(JsonNode action) {
        String rawReservationId = action.path("value")
                                        .asText(null);

        if (rawReservationId == null || rawReservationId.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(rawReservationId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isMissingReservationId(Long reservationId) {
        return reservationId == null;
    }

    private Optional<ReviewReservation> cancelReservationOrFallback(
            String teamId,
            String channelId,
            String slackUserId,
            Long reservationId,
            ReviewReservation reservation
    ) {
        Optional<ReviewReservation> cancelled = reviewReservationCoordinator.cancel(reservationId);
        if (cancelled.isPresent()) {
            publishCancelEvent(teamId, channelId, slackUserId, reservation);
            return cancelled;
        }
        return Optional.of(reservation);
    }

    private ReviewReservation findReservationOrNotify(
            Long reservationId,
            String token,
            String channelId,
            String slackUserId
    ) {
        return reviewReservationCoordinator.findById(reservationId)
                                           .orElseGet(() -> {
                                               errorNotifier.notify(
                                                       token,
                                                       channelId,
                                                       slackUserId,
                                                       InteractivityErrorType.RESERVATION_NOT_FOUND
                                               );
                                               return null;
                                           });
    }

    private boolean isOwnerOrNotify(
            ReviewReservation reservation,
            String token,
            String channelId,
            String slackUserId,
            InteractivityErrorType failureType
    ) {
        if (!slackUserId.equals(reservation.getReviewerSlackId())) {
            errorNotifier.notify(token, channelId, slackUserId, failureType);
            return false;
        }

        return true;
    }

    private boolean isCancelableReservationOrNotify(
            ReviewReservation reservation,
            String token,
            String channelId,
            String slackUserId
    ) {
        if (reservation == null) {
            return false;
        }
        if (!reservation.isActive()) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.RESERVATION_ALREADY_CANCELLED);
            return false;
        }
        if (isReviewAlreadyStarted(reservation)) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.RESERVATION_ALREADY_STARTED);
            return false;
        }

        return isOwnerOrNotify(reservation, token, channelId, slackUserId, InteractivityErrorType.NOT_OWNER_CANCEL);
    }

    private boolean isChangeableReservationOrNotify(
            ReviewReservation reservation,
            String token,
            String channelId,
            String slackUserId
    ) {
        if (reservation == null) {
            return false;
        }
        if (!reservation.isActive()) {
            errorNotifier.notify(
                    token,
                    channelId,
                    slackUserId,
                    InteractivityErrorType.RESERVATION_CHANGE_NOT_ALLOWED_CANCELLED
            );
            return false;
        }
        if (isReviewAlreadyStarted(reservation)) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.RESERVATION_ALREADY_STARTED);
            return false;
        }

        return isOwnerOrNotify(reservation, token, channelId, slackUserId, InteractivityErrorType.NOT_OWNER_CHANGE);
    }

    private boolean isReviewAlreadyStarted(ReviewReservation reservation) {
        return !reservation.getScheduledAt().isAfter(Instant.now(clock));
    }

    private void openChangeModal(
            JsonNode payload,
            String channelId,
            String slackUserId,
            String token,
            ReviewReservation reservation
    ) {
        final String metaJson;
        try {
            metaJson = reviewScheduleMetaBuilder.buildForChange(reservation);
        } catch (RuntimeException e) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.RESERVATION_LOAD_FAILURE);
            return;
        }

        String triggerId = payload.path("trigger_id")
                                  .asText();
        Object view = slackViews.reviewTimeSubmitModal(metaJson);

        slackApiClient.openModal(token, triggerId, view);
    }

    private void publishCancelEvent(
            String teamId,
            String channelId,
            String slackUserId,
            ReviewReservation reservation
    ) {
        ReviewReservationCancelEvent event = new ReviewReservationCancelEvent(
                teamId,
                channelId,
                slackUserId,
                reservation.getId(),
                reservation.getProjectId(),
                reservation.getReservationPullRequest().getPullRequestId()
        );

        reviewInteractionEventPublisher.publish(event);
    }

    private void publishChangeEvent(
            String teamId,
            String channelId,
            String slackUserId,
            ReviewReservation reservation
    ) {
        ReviewReservationChangeEvent event = new ReviewReservationChangeEvent(
                teamId,
                channelId,
                slackUserId,
                reservation.getId(),
                reservation.getProjectId(),
                reservation.getReservationPullRequest().getPullRequestId()
        );

        reviewInteractionEventPublisher.publish(event);
    }
}
