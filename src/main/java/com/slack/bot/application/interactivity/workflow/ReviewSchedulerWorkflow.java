package com.slack.bot.application.interactivity.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.publisher.ReviewReservationRequestEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.ProjectIdResolver;
import com.slack.bot.application.interactivity.reservation.ReservationMetaResolver;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.exception.ReservationMetaInvalidException;
import com.slack.bot.application.interactivity.view.factory.ReviewReservationTimeViewFactory;
import com.slack.bot.application.interactivity.workflow.dto.SchedulerContextDto;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewSchedulerWorkflow {

    private final ProjectIdResolver projectIdResolver;
    private final NotificationApiClient slackApiClient;
    private final SlackActionErrorNotifier errorNotifier;
    private final ReviewReservationTimeViewFactory slackViews;
    private final ReservationMetaResolver reservationMetaResolver;
    private final ReviewReservationCoordinator reviewReservationCoordinator;
    private final ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    public Optional<ReviewReservation> handleOpenScheduler(
            JsonNode payload,
            JsonNode action,
            String teamId,
            String channelId,
            String slackUserId,
            String token
    ) {
        SchedulerContextDto context = buildContext(payload, action, teamId, channelId, slackUserId, token);

        if (context == null) {
            return Optional.empty();
        }

        try {
            ReviewScheduleMetaDto meta = parseMeta(context.metaJson());
            Long projectId = projectIdResolver.resolve(meta.projectId(), context.teamId());

            publishReservationRequest(context);
            return findActiveReservation(meta, context, projectId).or(() -> {
                        openReviewTimeModal(context);
                        return Optional.empty();
                    });
        } catch (ReservationMetaInvalidException e) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.INVALID_META);

            return Optional.empty();
        } catch (IllegalStateException e) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.RESERVATION_LOAD_FAILURE);

            return Optional.empty();
        }
    }

    private ReviewScheduleMetaDto parseMeta(String metaJson) {
        return reservationMetaResolver.parseMeta(metaJson);
    }

    private SchedulerContextDto buildContext(JsonNode payload,
            JsonNode action,
            String teamId,
            String channelId,
            String slackUserId,
            String token) {
        String metaJson = action.path("value")
                                .asText(null);

        if (metaJson == null || metaJson.isBlank()) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.INVALID_META);
            return null;
        }

        String triggerId = payload.path("trigger_id")
                                  .asText();

        return new SchedulerContextDto(
                payload,
                action,
                teamId,
                channelId,
                slackUserId,
                token,
                triggerId,
                metaJson
        );
    }

    private Optional<ReviewReservation> findActiveReservation(
            ReviewScheduleMetaDto meta,
            SchedulerContextDto context,
            Long projectId
    ) {
        return reviewReservationCoordinator.findActive(
                meta.teamId(),
                projectId,
                context.slackUserId()
        );
    }

    private void openReviewTimeModal(SchedulerContextDto context) {
        Object view = slackViews.reviewTimeSubmitModal(context.metaJson());

        slackApiClient.openModal(context.token(), context.triggerId(), view);
    }

    private void publishReservationRequest(SchedulerContextDto context) {
        ReviewReservationRequestEvent event = new ReviewReservationRequestEvent(
                context.teamId(),
                context.channelId(),
                context.slackUserId(),
                context.metaJson()
        );

        reviewInteractionEventPublisher.publish(event);
    }
}
