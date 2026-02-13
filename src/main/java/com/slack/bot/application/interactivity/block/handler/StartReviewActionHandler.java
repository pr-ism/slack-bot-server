package com.slack.bot.application.interactivity.block.handler;

import com.slack.bot.application.interactivity.block.BlockActionHandler;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.block.handler.store.StartReviewMarkStore;
import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.notification.NotificationDispatcher;
import com.slack.bot.application.interactivity.notification.ReviewReservationNotifier;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.publisher.ReviewReservationFulfilledEvent;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.AuthorResolver;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import com.slack.bot.application.interactivity.reservation.ReservationMetaResolver;
import com.slack.bot.application.interactivity.reservation.exception.ReservationMetaInvalidException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartReviewActionHandler implements BlockActionHandler {

    private static final String START_REVIEW_ACK_MESSAGE = "리뷰 시작 알림을 전송했습니다.";
    private static final String ALREADY_STARTED_MESSAGE = "이미 해당 PR에 대한 리뷰를 시작했습니다.";
    private static final Duration START_REVIEW_MARK_TTL = Duration.ofDays(7);

    private final Clock clock;
    private final AuthorResolver authorResolver;
    private final StartReviewMarkStore startReviewMarkStore;
    private final NotificationDispatcher notificationDispatcher;
    private final ReservationMetaResolver reservationMetaResolver;
    private final SlackActionErrorNotifier errorNotifier;
    private final ReviewReservationNotifier reviewReservationNotifier;
    private final ReviewReservationCoordinator reviewReservationCoordinator;
    private final ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        String metaJson = command.action().path("value").asText(null);

        if (metaJson == null || metaJson.isBlank()) {
            return BlockActionOutcomeDto.empty();
        }

        ReviewScheduleMetaDto meta = parseMeta(metaJson);
        if (meta == null) {
            return BlockActionOutcomeDto.empty();
        }

        if (isRevieweeRequester(meta, command.slackUserId())) {
            errorNotifier.notify(
                    command.botToken(),
                    command.channelId(),
                    command.slackUserId(),
                    InteractivityErrorType.REVIEWEE_CANNOT_RESERVE
            );
            return BlockActionOutcomeDto.empty();
        }

        String duplicatePreventionKey = createDuplicatePreventionKey(meta, command.slackUserId());
        if (!tryMarkStarted(duplicatePreventionKey)) {
            notificationDispatcher.sendEphemeral(
                    command.botToken(),
                    command.channelId(),
                    command.slackUserId(),
                    ALREADY_STARTED_MESSAGE
            );
            return BlockActionOutcomeDto.empty();
        }

        try {
            cancelActiveReservation(meta, command.slackUserId());
        } catch (RuntimeException e) {
            rollbackStartedMark(duplicatePreventionKey);
            throw e;
        }

        try {
            reviewReservationNotifier.notifyStartNowToParticipants(
                    meta,
                    command.slackUserId(),
                    command.botToken()
            );
            notificationDispatcher.sendEphemeral(
                    command.botToken(),
                    command.channelId(),
                    command.slackUserId(),
                    START_REVIEW_ACK_MESSAGE
            );
            publishReviewFulfilledEvent(meta, command.slackUserId());
        } catch (SlackBotMessageDispatchException e) {
            log.warn("리뷰 시작 알림 전송 실패", e);
        } catch (RuntimeException e) {
            log.error("예상치 못한 리뷰 시작 알림 전송 실패", e);
        }

        return BlockActionOutcomeDto.empty();
    }

    private ReviewScheduleMetaDto parseMeta(String metaJson) {
        try {
            return reservationMetaResolver.parseMeta(metaJson);
        } catch (ReservationMetaInvalidException e) {
            log.warn("리뷰 바로 시작 메타 파싱 실패", e);
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

    private String createDuplicatePreventionKey(ReviewScheduleMetaDto meta, String reviewerSlackUserId) {
        String teamId = meta.teamId() == null ? "" : meta.teamId();
        String projectId = meta.projectId() == null ? "" : meta.projectId();
        String pullRequestId = meta.pullRequestId() == null ? "" : String.valueOf(meta.pullRequestId());
        String reviewerId = reviewerSlackUserId == null ? "" : reviewerSlackUserId;

        return teamId + ":" + projectId + ":" + pullRequestId + ":" + reviewerId;
    }

    private boolean tryMarkStarted(String key) {
        Instant now = Instant.now(clock);
        Instant existing = startReviewMarkStore.putIfAbsent(key, now);
        if (existing == null) {
            return true;
        }

        if (!isExpired(existing, now)) {
            return false;
        }

        if (!startReviewMarkStore.remove(key, existing)) {
            return false;
        }

        return startReviewMarkStore.putIfAbsent(key, now) == null;
    }

    private boolean isExpired(Instant markedAt, Instant now) {
        return markedAt.plus(START_REVIEW_MARK_TTL).isBefore(now);
    }

    private void rollbackStartedMark(String key) {
        startReviewMarkStore.remove(key);
    }

    private void cancelActiveReservation(ReviewScheduleMetaDto meta, String reviewerSlackUserId) {
        Long projectId = parseProjectId(meta.projectId());

        if (projectId == null) {
            return;
        }

        reviewReservationCoordinator.cancelActive(
                meta.teamId(),
                projectId,
                reviewerSlackUserId,
                meta.pullRequestId()
        );
    }

    private Long parseProjectId(String rawProjectId) {
        if (rawProjectId == null || rawProjectId.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(rawProjectId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void publishReviewFulfilledEvent(ReviewScheduleMetaDto meta, String reviewerSlackUserId) {
        if (meta == null) {
            return;
        }

        Long projectId = parseProjectId(meta.projectId());
        if (projectId == null) {
            return;
        }
        if (meta.teamId() == null || meta.teamId().isBlank()) {
            return;
        }
        if (reviewerSlackUserId == null || reviewerSlackUserId.isBlank()) {
            return;
        }
        if (meta.pullRequestId() == null) {
            return;
        }

        ReviewReservationFulfilledEvent event = new ReviewReservationFulfilledEvent(
                meta.teamId(),
                projectId,
                reviewerSlackUserId,
                meta.pullRequestId(),
                Instant.now(clock)
        );
        reviewInteractionEventPublisher.publish(event);
    }
}
