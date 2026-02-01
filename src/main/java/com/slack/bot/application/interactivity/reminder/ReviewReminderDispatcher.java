package com.slack.bot.application.interactivity.reminder;

import com.slack.bot.application.interactivity.client.ReviewReminderSlackDirectMessageClient;
import com.slack.bot.domain.reservation.ReviewReminder;
import com.slack.bot.domain.reservation.repository.ReviewReminderRepository;
import com.slack.bot.domain.reservation.vo.ReminderParticipants;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.domain.setting.repository.NotificationSettingsRepository;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.ReviewReminderMessageProperties;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewReminderDispatcher {

    private final Clock clock;
    private final WorkspaceRepository workspaceRepository;
    private final ReviewReminderRepository reviewReminderRepository;
    private final ReviewReminderSlackDirectMessageClient reviewReminderSlackDirectMessageClient;
    private final ReviewReminderMessageProperties messageProperties;
    private final NotificationSettingsRepository notificationSettingsRepository;

    public void send(ReviewReminder reviewReminder) {
        resolveToken(reviewReminder.getDestination().getTeamId()).ifPresentOrElse(
                token -> dispatch(token, reviewReminder),
                () -> {
                    log.warn("워크스페이스 토큰을 찾을 수 없습니다. teamId={}", reviewReminder.getDestination().getTeamId());
                    markFired(reviewReminder);
                }
        );
    }

    private Optional<String> resolveToken(String teamId) {
        return workspaceRepository.findByTeamId(teamId)
                                  .map(workspace -> workspace.getAccessToken())
                                  .filter(token -> token != null && !token.isBlank());
    }

    private void dispatch(String token, ReviewReminder reviewReminder) {
        String teamId = reviewReminder.getDestination().getTeamId();
        String reviewerSlackId = reviewReminder.getParticipants().getReviewerSlackId();

        if (isNotReviewReminderNotificationActive(teamId, reviewerSlackId)) {
            markFired(reviewReminder);
            return;
        }

        sendDirectMessages(token, reviewReminder);
        markFired(reviewReminder);
    }

    private boolean isNotReviewReminderNotificationActive(String teamId, String reviewerSlackId) {
        return !notificationSettingsRepository.findBySlackUser(teamId, reviewerSlackId)
                                              .map(NotificationSettings::getOptionalNotifications)
                                              .map(notifications -> notifications.isReviewReminderEnabled())
                                              .orElse(true);
    }

    private void sendDirectMessages(String token, ReviewReminder reviewReminder) {
        try {
            Long reservationId = reviewReminder.getReservationId();
            ReminderParticipants participants = reviewReminder.getParticipants();

            sendMessage(
                    token,
                    participants.getPullRequestAuthorSlackId(),
                    buildAuthorMessage(reviewReminder),
                    reservationId
            );

            sendMessage(
                    token,
                    participants.getReviewerSlackId(),
                    buildReviewerMessage(reviewReminder),
                    reservationId
            );

            log.info("리뷰 리마인더 전송 완료. reservationId={}", reservationId);
        } catch (Exception e) {
            log.error("리뷰 리마인더 전송 실패. reservationId={}", reviewReminder.getReservationId(), e);
        }
    }

    private void sendMessage(String token, String slackId, String message, Long reservationId) {
        if (slackId == null || slackId.isBlank()) {
            log.warn("대상 슬랙 아이디가 존재하지 않아 전송을 건너뜁니다. reservationId={}", reservationId);
            return;
        }

        reviewReminderSlackDirectMessageClient.send(token, slackId, message);
    }

    private void markFired(ReviewReminder reviewReminder) {
        reviewReminder.markFired(clock.instant());
        reviewReminderRepository.save(reviewReminder);
    }

    private String buildAuthorMessage(ReviewReminder reminder) {
        return messageProperties.reviewee()
                                .formatted(
                                        formatReviewerMention(reminder.getParticipants().getReviewerSlackId()),
                                        formatPullRequestLink(reminder)
                                );
    }

    private String buildReviewerMessage(ReviewReminder reviewReminder) {
        return messageProperties.reviewer()
                                .formatted(
                                        reviewReminder.getPullRequest().getPullRequestTitle(),
                                        reviewReminder.getPullRequest().getPullRequestUrl()
                                );
    }

    private String formatReviewerMention(String reviewerSlackId) {
        if (reviewerSlackId == null || reviewerSlackId.isBlank()) {
            return "";
        }

        return "<@" + reviewerSlackId + ">";
    }

    private String formatPullRequestLink(ReviewReminder reminder) {
        String url = reminder.getPullRequest().getPullRequestUrl();
        String title = reminder.getPullRequest().getPullRequestTitle();

        if (url == null || url.isBlank()) {
            return title == null ? "" : title;
        }
        if (title == null || title.isBlank()) {
            return url;
        }

        return "<" + url + "|" + title + ">";
    }
}
