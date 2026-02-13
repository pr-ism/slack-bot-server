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

    private static final String DUPLICATE_RESERVATION_MESSAGE = "이미 이 PR 리뷰를 예약했습니다.";
    private static final String ALREADY_STARTED_RESERVATION_MESSAGE = "이미 리뷰 시작 시간이 되어 새로 예약할 수 없습니다.";

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

    public void sendReservationBlockToDmAndEphemeral(
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
        String ephemeralText = buildReservationEphemeralText(reservation, headerText);

        notificationDispatcher.sendReservationBlockBySettingOrDefault(
                token,
                teamId,
                channelId,
                slackUserId,
                message.blocks(),
                message.fallbackText(),
                ephemeralText
        );
    }

    public void sendReservationBlockToDirectMessageOnly(
            String token,
            String slackUserId,
            ReviewReservation reservation,
            String headerText
    ) {
        ReviewReservationMessageDto message = reservationBlockCreator.create(
                reservation,
                headerText,
                ReviewReservationBlockType.RESERVATION
        );

        notificationDispatcher.sendBlockToDirectMessageOnly(
                token,
                slackUserId,
                message.blocks(),
                message.fallbackText()
        );
    }

    public void sendDuplicateReservationNoticeToDmAndEphemeral(
            String token,
            String channelId,
            String slackUserId,
            ReviewReservation reservation
    ) {
        String duplicateMessage = resolveDuplicateReservationMessage(reservation);
        ReviewReservationMessageDto message = reservationBlockCreator.create(
                reservation,
                duplicateMessage,
                ReviewReservationBlockType.RESERVATION
        );

        notificationDispatcher.sendEphemeral(
                token,
                channelId,
                slackUserId,
                duplicateMessage
        );
        notificationDispatcher.sendBlockToDirectMessageOnly(
                token,
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

    public void notifyStartNowToParticipants(ReviewScheduleMetaDto meta, String reviewerId, String token) {
        if (meta == null) {
            return;
        }
        if (reviewerId == null || reviewerId.isBlank()) {
            return;
        }

        String authorSlackId = meta.authorSlackId();

        if (authorSlackId == null || authorSlackId.isBlank()) {
            return;
        }

        String text = messageFormatter.buildStartNowText(authorSlackId, reviewerId, meta);

        notificationDispatcher.sendDirectMessageBySettingOrDefault(meta.teamId(), token, authorSlackId, text);
        notificationDispatcher.sendDirectMessageBySettingOrDefault(meta.teamId(), token, reviewerId, text);
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
        if (scheduledAt == null) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime scheduledAtZoned = scheduledAt.atZone(clock.getZone()).truncatedTo(ChronoUnit.MINUTES);
        String text = messageFormatter.buildScheduledText(authorSlackId, reviewerId, now, scheduledAtZoned, meta);

        notificationDispatcher.sendDirectMessageIfEnabled(meta.teamId(), token, authorSlackId, text);
        notificationDispatcher.sendDirectMessageIfEnabled(meta.teamId(), token, reviewerId, text);
    }

    private String resolveDuplicateReservationMessage(ReviewReservation reservation) {
        if (reservation == null || reservation.getScheduledAt() == null) {
            return DUPLICATE_RESERVATION_MESSAGE;
        }

        if (!reservation.getScheduledAt().isAfter(Instant.now(clock))) {
            return ALREADY_STARTED_RESERVATION_MESSAGE;
        }

        return DUPLICATE_RESERVATION_MESSAGE;
    }

    private String buildReservationEphemeralText(ReviewReservation reservation, String headerText) {
        String title = reservation.getReservationPullRequest().getPullRequestTitle();
        String prLine = (title != null && !title.isBlank())
                ? title
                : "PR #" + reservation.getReservationPullRequest().getPullRequestNumber();
        ZonedDateTime when = reservation.getScheduledAt().atZone(clock.getZone());
        String scheduledAtText = String.format(
                "%d년 %d월 %d일 %02d시 %02d분",
                when.getYear(),
                when.getMonthValue(),
                when.getDayOfMonth(),
                when.getHour(),
                when.getMinute()
        );

        return headerText + "\n" + prLine + "\n리뷰 시작 시간: " + scheduledAtText;
    }
}
