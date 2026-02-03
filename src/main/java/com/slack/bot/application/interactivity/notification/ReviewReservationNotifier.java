package com.slack.bot.application.interactivity.notification;

import com.slack.bot.application.interactivity.block.ReviewReservationBlockCreator;
import com.slack.bot.application.interactivity.block.ReviewReservationBlockType;
import com.slack.bot.application.interactivity.block.dto.ReviewReservationMessageDto;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewReservationNotifier {

    private final Clock clock;
    private final NotificationDispatcher notificationDispatcher;
    private final ReviewReservationBlockCreator reservationBlockCreator;
    private final ReviewNotificationMessageFormatter messageFormatter;

    public void sendReservationBlock(
            String token,
            String teamId,
            String channelId,
            String slackUserId,
            ReviewReservation reservation,
            String headerText
    ) {
        ReviewReservationMessageDto message = reservationBlockCreator.create(
                reservation,
                headerText,
                ReviewReservationBlockType.RESERVATION
        );

        notificationDispatcher.sendBlock(
                teamId,
                token,
                channelId,
                slackUserId,
                message.blocks(),
                message.fallbackText()
        );
    }

    public void sendCancellationMessage(
            String token,
            String channelId,
            String slackUserId,
            ReviewReservation reservation
    ) {
        ReviewReservationMessageDto message = reservationBlockCreator.create(
                reservation,
                "*리뷰 예약을 취소했습니다.*",
                ReviewReservationBlockType.CANCELLATION
        );
        notificationDispatcher.sendBlock(
                reservation.getTeamId(),
                token,
                channelId,
                slackUserId,
                message.blocks(),
                message.fallbackText()
        );
    }

    public void notifyStartNow(ReviewScheduleMetaDto meta, String reviewerId, String token, String authorSlackId) {
        if (meta == null) {
            return;
        }
        if (authorSlackId == null || authorSlackId.isBlank()) {
            return;
        }
        if (reviewerId == null || reviewerId.isBlank()) {
            return;
        }

        String text = messageFormatter.buildStartNowText(authorSlackId, reviewerId, meta);

        notificationDispatcher.sendDirectMessageIfEnabled(meta.teamId(), token, authorSlackId, text);
        notificationDispatcher.sendDirectMessageIfEnabled(meta.teamId(), token, reviewerId, text);
    }

    public void notifyScheduled(
            ReviewScheduleMetaDto meta,
            String reviewerId,
            Instant scheduledAt,
            String token,
            String authorSlackId
    ) {
        if (meta == null) {
            return;
        }
        if (authorSlackId == null || authorSlackId.isBlank()) {
            return;
        }
        if (reviewerId == null || reviewerId.isBlank()) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime scheduledAtZoned = scheduledAt.atZone(clock.getZone()).truncatedTo(ChronoUnit.MINUTES);
        String text = messageFormatter.buildScheduledText(authorSlackId, reviewerId, now, scheduledAtZoned, meta);

        notificationDispatcher.sendDirectMessageIfEnabled(meta.teamId(), token, authorSlackId, text);
        notificationDispatcher.sendDirectMessageIfEnabled(meta.teamId(), token, reviewerId, text);
    }
}
