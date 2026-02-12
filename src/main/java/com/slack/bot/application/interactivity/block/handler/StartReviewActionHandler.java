package com.slack.bot.application.interactivity.block.handler;

import com.slack.bot.application.interactivity.block.BlockActionHandler;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.notification.NotificationDispatcher;
import com.slack.bot.application.interactivity.notification.ReviewReservationNotifier;
import com.slack.bot.application.interactivity.reply.InteractivityErrorType;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.AuthorResolver;
import com.slack.bot.application.interactivity.reservation.ReservationMetaResolver;
import com.slack.bot.application.interactivity.reservation.exception.ReservationMetaInvalidException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ReservationMetaResolver reservationMetaResolver;
    private final ReviewReservationNotifier reviewReservationNotifier;
    private final NotificationDispatcher notificationDispatcher;
    private final SlackActionErrorNotifier errorNotifier;
    private final Map<String, Instant> startedReviewMarks = new ConcurrentHashMap<>();

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        String metaJson = command.action().path("value").asText(null);

        if (metaJson == null || metaJson.isBlank()) {
            return BlockActionOutcomeDto.empty();
        }

        try {
            ReviewScheduleMetaDto meta = reservationMetaResolver.parseMeta(metaJson);

            if (isRevieweeRequester(meta, command.slackUserId())) {
                errorNotifier.notify(
                        command.botToken(),
                        command.channelId(),
                        command.slackUserId(),
                        InteractivityErrorType.REVIEWEE_CANNOT_RESERVE
                );
                return BlockActionOutcomeDto.empty();
            }
            String dedupKey = dedupKey(meta, command.slackUserId());
            if (isAlreadyStarted(dedupKey)) {
                notificationDispatcher.sendEphemeral(
                        command.botToken(),
                        command.channelId(),
                        command.slackUserId(),
                        ALREADY_STARTED_MESSAGE
                );
                return BlockActionOutcomeDto.empty();
            }

            reviewReservationNotifier.notifyStartNowToReviewee(
                    meta,
                    command.slackUserId(),
                    command.botToken()
            );
            markStarted(dedupKey);
            notificationDispatcher.sendEphemeral(
                    command.botToken(),
                    command.channelId(),
                    command.slackUserId(),
                    START_REVIEW_ACK_MESSAGE
            );
        } catch (ReservationMetaInvalidException e) {
            log.warn("리뷰 바로 시작 메타 파싱 실패", e);
        }

        return BlockActionOutcomeDto.empty();
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

    private String dedupKey(ReviewScheduleMetaDto meta, String reviewerSlackUserId) {
        String teamId = meta.teamId() == null ? "" : meta.teamId();
        String projectId = meta.projectId() == null ? "" : meta.projectId();
        String pullRequestId = meta.pullRequestId() == null ? "" : String.valueOf(meta.pullRequestId());
        String reviewerId = reviewerSlackUserId == null ? "" : reviewerSlackUserId;

        return teamId + ":" + projectId + ":" + pullRequestId + ":" + reviewerId;
    }

    private boolean isAlreadyStarted(String key) {
        Instant markedAt = startedReviewMarks.get(key);
        if (markedAt == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        if (markedAt.plus(START_REVIEW_MARK_TTL).isBefore(now)) {
            startedReviewMarks.remove(key);
            return false;
        }

        return true;
    }

    private void markStarted(String key) {
        startedReviewMarks.put(key, Instant.now(clock));
    }
}
