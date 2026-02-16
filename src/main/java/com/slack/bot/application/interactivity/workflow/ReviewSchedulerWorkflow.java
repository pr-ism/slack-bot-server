package com.slack.bot.application.interactivity.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.publisher.ReviewReservationRequestEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.AuthorResolver;
import com.slack.bot.application.interactivity.reservation.ProjectIdResolver;
import com.slack.bot.application.interactivity.reservation.ReservationMetaResolver;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.exception.ReservationMetaInvalidException;
import com.slack.bot.application.interactivity.view.factory.ReviewReservationTimeViewFactory;
import com.slack.bot.application.interactivity.workflow.dto.SchedulerContextDto;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSchedulerWorkflow {

    private final AuthorResolver authorResolver;
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

        ReviewScheduleMetaDto meta = parseMetaSafely(context);

        if (meta == null) {
            return Optional.empty();
        }

        if (isRevieweeRequester(meta, context.slackUserId())) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.REVIEWEE_CANNOT_RESERVE);
            return Optional.empty();
        }

        try {
            Long projectId = projectIdResolver.resolve(meta.projectId(), context.teamId());
            publishReservationRequest(context, meta, projectId);

            return findActiveReservation(meta, context, projectId).or(() -> {
                openReviewTimeModal(context);
                return Optional.empty();
            });
        } catch (IllegalStateException e) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.RESERVATION_LOAD_FAILURE);
            return Optional.empty();
        }
    }

    private ReviewScheduleMetaDto parseMeta(String metaJson) {
        return reservationMetaResolver.parseMeta(metaJson);
    }

    private ReviewScheduleMetaDto parseMetaSafely(SchedulerContextDto context) {
        try {
            return parseMeta(context.metaJson());
        } catch (ReservationMetaInvalidException e) {
            log.warn("리뷰 예약 메타 파싱 실패: {}", e.getMessage());
            errorNotifier.notify(
                    context.token(),
                    context.channelId(),
                    context.slackUserId(),
                    InteractivityErrorType.INVALID_META
            );
            return null;
        }
    }

    private boolean isRevieweeRequester(ReviewScheduleMetaDto meta, String requesterSlackUserId) {
        if (meta == null || requesterSlackUserId == null || requesterSlackUserId.isBlank()) {
            return false;
        }

        String authorSlackId = authorResolver.resolveAuthorSlackId(meta);

        if (authorSlackId == null || authorSlackId.isBlank()) {
            return false;
        }

        return requesterSlackUserId.equals(authorSlackId);
    }

    private SchedulerContextDto buildContext(
            JsonNode payload,
            JsonNode action,
            String teamId,
            String channelId,
            String slackUserId,
            String token
    ) {
        String metaJson = action.path("value")
                                .asText(null);

        if (metaJson == null || metaJson.isBlank()) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.INVALID_META);
            return null;
        }

        String triggerId = payload.path("trigger_id")
                                  .asText(null);

        if (triggerId == null || triggerId.isBlank()) {
            errorNotifier.notify(token, channelId, slackUserId, InteractivityErrorType.INVALID_META);
            return null;
        }

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
                context.slackUserId(),
                meta.pullRequestId()
        );
    }

    private void openReviewTimeModal(SchedulerContextDto context) {
        log.info(
                "리뷰 예약 모달 오픈 시도. teamId={}, userId={}, triggerIdPrefix={}, metaLength={}",
                context.teamId(),
                context.slackUserId(),
                abbreviate(context.triggerId()),
                context.metaJson() == null ? 0 : context.metaJson().length()
        );
        Object view = slackViews.reviewTimeSubmitModal(context.metaJson());

        slackApiClient.openModal(context.token(), context.triggerId(), view);
    }

    private void publishReservationRequest(
            SchedulerContextDto context,
            ReviewScheduleMetaDto meta,
            Long projectId
    ) {
        ReviewReservationRequestEvent event = new ReviewReservationRequestEvent(
                context.teamId(),
                context.channelId(),
                context.slackUserId(),
                projectId,
                meta.pullRequestId(),
                context.metaJson()
        );

        reviewInteractionEventPublisher.publish(event);
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "empty";
        }
        int endIndex = Math.min(value.length(), 12);
        return value.substring(0, endIndex);
    }
}
