package com.slack.bot.application.interactivity.view;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewTimeValidator {

    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}$");
    private final Clock clock;

    public Map<String, String> validateCustomDateTime(String dateString, String timeString) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (dateString == null || dateString.isBlank()) {
            errors.put("date_block", "날짜를 선택해주세요.");
            return errors;
        }
        String trimmedDate = dateString.trim();
        if (timeString == null || timeString.isBlank()) {
            errors.put("time_block", "시간을 입력해주세요. 예: 21:35");
            return errors;
        }

        String normalizedTime = normalizeTimeOrNull(timeString);

        if (normalizedTime == null) {
            errors.put("time_block", "시간은 00:00~23:59 범위의 HH:mm 형식이어야 합니다. 예: 21:35");
            return errors;
        }

        Instant scheduledAt = parseScheduledAtOrNull(trimmedDate, normalizedTime);
        if (scheduledAt == null) {
            errors.put("time_block", "날짜/시간 값이 올바르지 않습니다. 예: 21:35");
            return errors;
        }

        ZonedDateTime nowMinute = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime scheduledAtMinute = scheduledAt.atZone(clock.getZone()).truncatedTo(ChronoUnit.MINUTES);

        if (scheduledAtMinute.isBefore(nowMinute)) {
            errors.put("time_block", "과거 시간은 선택할 수 없습니다. 현재 이후 시간으로 입력해주세요.");
        }

        return errors;
    }

    public String normalizeTimeOrNull(String input) {
        if (input == null) {
            return null;
        }

        String trimmedTime = input.trim();

        if (!TIME_PATTERN.matcher(trimmedTime).matches()) {
            return null;
        }

        int hour = (trimmedTime.charAt(0) - '0') * 10 + (trimmedTime.charAt(1) - '0');
        int minute = (trimmedTime.charAt(3) - '0') * 10 + (trimmedTime.charAt(4) - '0');

        if (hour < 0 || hour > 23) {
            return null;
        }
        if (minute < 0 || minute > 59) {
            return null;
        }

        return trimmedTime;
    }

    public Instant parseScheduledAtInstant(String dateString, String timeString) {
        if (dateString == null || dateString.isBlank()) {
            throw new IllegalArgumentException("날짜 값이 필요합니다.");
        }
        if (timeString == null || timeString.isBlank()) {
            throw new IllegalArgumentException("시간 값이 필요합니다.");
        }

        String normalizedTime = normalizeTimeOrNull(timeString);
        if (normalizedTime == null) {
            throw new IllegalArgumentException("시간 형식은 HH:mm 이어야 합니다.");
        }
        String[] timeParts = normalizedTime.split(":");

        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("시간은 0~23 범위여야 합니다.");
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("분은 0~59 범위여야 합니다.");
        }

        LocalDate localDate = LocalDate.parse(dateString);
        LocalDateTime localDateTime = localDate.atTime(hour, minute);

        return localDateTime.atZone(clock.getZone()).toInstant();
    }

    private Instant parseScheduledAtOrNull(String dateString, String timeString) {
        try {
            return parseScheduledAtInstant(dateString, timeString);
        } catch (Exception e) {
            return null;
        }
    }
}
