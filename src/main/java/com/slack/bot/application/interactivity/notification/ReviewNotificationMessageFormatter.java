package com.slack.bot.application.interactivity.notification;

import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import java.time.Duration;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class ReviewNotificationMessageFormatter {

    String formatDelay(long totalMinutes) {
        if (totalMinutes <= 0) {
            return "곧 ";
        }

        if (totalMinutes < 60) {
            return totalMinutes + "분 뒤";
        }

        if (totalMinutes < 24 * 60) {
            long hours = totalMinutes / 60;
            return hours + "시간 뒤";
        }

        long days = totalMinutes / (24 * 60);
        long remain = totalMinutes % (24 * 60);
        long hours = remain / 60;
        long minutes = remain % 60;

        return days + "일 " + hours + "시간 " + minutes + "분 뒤";
    }

    String formatScheduledAtText(ZonedDateTime now, ZonedDateTime scheduledAt) {
        boolean sameDay = now.toLocalDate().equals(scheduledAt.toLocalDate());

        int hour = scheduledAt.getHour();
        int minute = scheduledAt.getMinute();

        if (sameDay) {
            return String.format("%02d시 %02d분", hour, minute);
        }

        int year = scheduledAt.getYear();
        int month = scheduledAt.getMonthValue();
        int day = scheduledAt.getDayOfMonth();

        return String.format("%d년 %d월 %d일 %02d시 %02d분", year, month, day, hour, minute);
    }

    String formatPullRequestLine(ReviewScheduleMetaDto meta) {
        if (meta == null) {
            return "";
        }
        String title = meta.pullRequestTitle();
        String url = meta.pullRequestUrl();
        if (url != null && !url.isBlank() && title != null && !title.isBlank()) {
            return "<" + url + "|" + title + ">";
        }
        if (url != null && !url.isBlank()) {
            return url;
        }

        int pullRequestNumber = meta.pullRequestNumber();

        return "PR #" + pullRequestNumber;
    }

    public String buildStartNowText(String authorSlackId, String reviewerId, ReviewScheduleMetaDto meta) {
        return "<@" + authorSlackId + "> 지금부터 <@" + reviewerId + "> 님이 리뷰를 시작합니다.\n" +
                formatPullRequestLine(meta);
    }

    public String buildScheduledText(
            String authorSlackId,
            String reviewerId,
            ZonedDateTime now,
            ZonedDateTime scheduledAt,
            ReviewScheduleMetaDto meta
    ) {
        long totalMinutes = Duration.between(now.toInstant(), scheduledAt.toInstant()).toMinutes();

        if (totalMinutes < 0) {
            totalMinutes = 0;
        }

        String delayText = formatDelay(totalMinutes);
        String scheduledAtText = formatScheduledAtText(now, scheduledAt);

        return "<@" + authorSlackId + "> <@" + reviewerId + "> 님이 " +
                delayText + "(" + scheduledAtText + ")에 리뷰를 시작하겠습니다.\n" +
                formatPullRequestLine(meta);
    }
}
