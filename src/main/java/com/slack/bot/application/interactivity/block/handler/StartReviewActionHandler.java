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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartReviewActionHandler implements BlockActionHandler {

    private static final String START_REVIEW_ACK_MESSAGE = "리뷰 시작 알림을 전송했습니다.";

    private final AuthorResolver authorResolver;
    private final ReservationMetaResolver reservationMetaResolver;
    private final ReviewReservationNotifier reviewReservationNotifier;
    private final NotificationDispatcher notificationDispatcher;
    private final SlackActionErrorNotifier errorNotifier;

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

            reviewReservationNotifier.notifyStartNowToReviewee(
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
}
