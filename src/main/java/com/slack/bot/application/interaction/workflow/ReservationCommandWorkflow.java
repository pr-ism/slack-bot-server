package com.slack.bot.application.interaction.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.model.view.View;
import com.slack.bot.application.interaction.client.NotificationApiClient;
import com.slack.bot.application.interaction.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interaction.publisher.ReviewReservationCancelEvent;
import com.slack.bot.application.interaction.publisher.ReviewReservationChangeEvent;
import com.slack.bot.application.interaction.reply.InteractionErrorType;
import com.slack.bot.application.interaction.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interaction.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interaction.reservation.ReviewScheduleMetaBuilder;
import com.slack.bot.application.interaction.view.factory.ReviewReservationTimeViewFactory;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCommandWorkflow {

    private final Clock clock;
    private final NotificationApiClient slackApiClient;
    private final SlackActionErrorNotifier errorNotifier;
    private final ReviewReservationTimeViewFactory slackViews;
    private final ReviewScheduleMetaBuilder reviewScheduleMetaBuilder;
    private final ReviewReservationCoordinator reviewReservationCoordinator;
    private final ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    public Optional<ReviewReservation> handleCancel(
            JsonNode action,
            String teamId,
            String channelId,
            String slackUserId,
            String token
    ) {
        ReservationIdResolution reservationIdResolution = resolveReservationId(action);

        if (reservationIdResolution instanceof MissingReservationId) {
            return Optional.empty();
        }
        if (reservationIdResolution instanceof InvalidReservationId invalidReservationId) {
            notifyInvalidReservationId(token, channelId, slackUserId, invalidReservationId.rawValue());
            return Optional.empty();
        }

        ValidReservationId validReservationId = (ValidReservationId) reservationIdResolution;

        return findReservationOrNotify(validReservationId.reservationId(), token, channelId, slackUserId)
                .filter(reservation -> isCancelableReservationOrNotify(
                        reservation,
                        token,
                        channelId,
                        slackUserId
                ))
                .flatMap(reservation -> cancelReservationOrFallback(
                        teamId,
                        channelId,
                        slackUserId,
                        validReservationId.reservationId(),
                        reservation
                ));
    }

    public void handleChange(
            JsonNode payload,
            JsonNode action,
            String teamId,
            String channelId,
            String slackUserId,
            String token
    ) {
        ReservationIdResolution reservationIdResolution = resolveReservationId(action);

        if (reservationIdResolution instanceof MissingReservationId) {
            return;
        }
        if (reservationIdResolution instanceof InvalidReservationId invalidReservationId) {
            notifyInvalidReservationId(token, channelId, slackUserId, invalidReservationId.rawValue());
            return;
        }

        ValidReservationId validReservationId = (ValidReservationId) reservationIdResolution;

        findReservationOrNotify(validReservationId.reservationId(), token, channelId, slackUserId)
                .filter(reservation -> isChangeableReservationOrNotify(
                        reservation,
                        token,
                        channelId,
                        slackUserId
                ))
                .ifPresent(reservation -> {
                    publishChangeEvent(teamId, channelId, slackUserId, reservation);
                    openChangeModal(payload, channelId, slackUserId, token, reservation);
                });
    }

    private ReservationIdResolution resolveReservationId(JsonNode action) {
        String rawReservationId = action.path("value")
                                        .asText(null);

        return Optional.ofNullable(rawReservationId)
                       .filter(value -> !value.isBlank())
                       .<ReservationIdResolution>map(value -> parseReservationId(value)
                               .<ReservationIdResolution>map(reservationId -> new ValidReservationId(reservationId))
                               .orElseGet(() -> new InvalidReservationId(value)))
                       .orElseGet(() -> new MissingReservationId());
    }

    private Optional<ReviewReservation> cancelReservationOrFallback(
            String teamId,
            String channelId,
            String slackUserId,
            Long reservationId,
            ReviewReservation reservation
    ) {
        return reviewReservationCoordinator.cancel(reservationId)
                                          .map(cancelled -> {
                                              publishCancelEvent(teamId, channelId, slackUserId, reservation);
                                              return cancelled;
                                          })
                                          .or(() -> Optional.of(reservation));
    }

    private Optional<ReviewReservation> findReservationOrNotify(
            Long reservationId,
            String token,
            String channelId,
            String slackUserId
    ) {
        return reviewReservationCoordinator.findById(reservationId)
                                           .or(() -> {
                                               errorNotifier.notify(
                                                       token,
                                                       channelId,
                                                       slackUserId,
                                                       InteractionErrorType.RESERVATION_NOT_FOUND
                                               );
                                               return Optional.empty();
                                           });
    }

    private Optional<Long> parseReservationId(String rawReservationId) {
        try {
            Long reservationId = Long.parseLong(rawReservationId);

            return Optional.of(reservationId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void notifyInvalidReservationId(
            String token,
            String channelId,
            String slackUserId,
            String rawReservationId
    ) {
        log.warn("유효하지 않은 reservationId action value입니다. rawReservationId={}", rawReservationId);
        errorNotifier.notify(token, channelId, slackUserId, InteractionErrorType.RESERVATION_LOAD_FAILURE);
    }

    private boolean isOwnerOrNotify(
            ReviewReservation reservation,
            String token,
            String channelId,
            String slackUserId,
            InteractionErrorType failureType
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
        if (!reservation.isActive()) {
            errorNotifier.notify(token, channelId, slackUserId, InteractionErrorType.RESERVATION_ALREADY_CANCELLED);
            return false;
        }
        if (isReviewAlreadyStarted(reservation)) {
            errorNotifier.notify(token, channelId, slackUserId, InteractionErrorType.RESERVATION_ALREADY_STARTED);
            return false;
        }

        return isOwnerOrNotify(reservation, token, channelId, slackUserId, InteractionErrorType.NOT_OWNER_CANCEL);
    }

    private boolean isChangeableReservationOrNotify(
            ReviewReservation reservation,
            String token,
            String channelId,
            String slackUserId
    ) {
        if (!reservation.isActive()) {
            errorNotifier.notify(
                    token,
                    channelId,
                    slackUserId,
                    InteractionErrorType.RESERVATION_CHANGE_NOT_ALLOWED_CANCELLED
            );
            return false;
        }
        if (isReviewAlreadyStarted(reservation)) {
            errorNotifier.notify(token, channelId, slackUserId, InteractionErrorType.RESERVATION_ALREADY_STARTED);
            return false;
        }

        return isOwnerOrNotify(reservation, token, channelId, slackUserId, InteractionErrorType.NOT_OWNER_CHANGE);
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
        try {
            String metaJson = reviewScheduleMetaBuilder.buildForChange(reservation);
            String triggerId = payload.path("trigger_id")
                                      .asText();
            View view = slackViews.reviewTimeSubmitModal(metaJson);

            slackApiClient.openModal(token, triggerId, view);
        } catch (RuntimeException e) {
            errorNotifier.notify(token, channelId, slackUserId, InteractionErrorType.RESERVATION_LOAD_FAILURE);
        }
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
                reservation.getReservationPullRequest().getGithubPullRequestId()
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
                reservation.getReservationPullRequest().getGithubPullRequestId()
        );

        reviewInteractionEventPublisher.publish(event);
    }

    private sealed interface ReservationIdResolution permits MissingReservationId, InvalidReservationId, ValidReservationId {
    }

    private record MissingReservationId() implements ReservationIdResolution {
    }

    private record InvalidReservationId(String rawValue) implements ReservationIdResolution {
    }

    private record ValidReservationId(Long reservationId) implements ReservationIdResolution {
    }
}
