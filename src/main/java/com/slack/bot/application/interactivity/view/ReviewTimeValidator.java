package com.slack.bot.application.interactivity.view;

import java.time.Instant;
import java.time.Clock;
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

    private static final Pattern HHMM_PATTERN = Pattern.compile("^\\d{2}:\\d{2}$");
    private final Clock clock;

    public Map<String, String> validateCustomDateTime(String date, String hhmm) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (date == null || date.isBlank()) {
            errors.put("date_block", "날짜를 선택해주세요.");
            return errors;
        }
        if (hhmm == null || hhmm.isBlank()) {
            errors.put("time_block", "시간을 입력해주세요. 예: 21:35");
            return errors;
        }

        String normalized = normalizeHhMmOrNull(hhmm);

        if (normalized == null) {
            errors.put("time_block", "시간은 00:00~23:59 범위의 HH:mm 형식이어야 합니다. 예: 21:35");
            return errors;
        }

        Instant scheduledAt = parseScheduledAtOrNull(date, normalized);
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

    public String normalizeHhMmOrNull(String input) {
        if (input == null) {
            return null;
        }

        String t = input.trim();

        if (!HHMM_PATTERN.matcher(t).matches()) {
            return null;
        }

        int hour = (t.charAt(0) - '0') * 10 + (t.charAt(1) - '0');
        int minute = (t.charAt(3) - '0') * 10 + (t.charAt(4) - '0');

        if (hour < 0 || hour > 23) {
            return null;
        }
        if (minute < 0 || minute > 59) {
            return null;
        }

        return t;
    }

    public Instant parseScheduledAtInstant(String date, String hhmm) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("날짜 값이 필요합니다.");
        }
        if (hhmm == null || hhmm.isBlank()) {
            throw new IllegalArgumentException("시간 값이 필요합니다.");
        }

        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("시간 형식은 HH:mm 이어야 합니다.");
        }

        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("시간은 0~23 범위여야 합니다.");
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("분은 0~59 범위여야 합니다.");
        }

        LocalDate ld = LocalDate.parse(date);
        LocalDateTime localDateTime = ld.atTime(hour, minute);

        return localDateTime.atZone(clock.getZone()).toInstant();
    }

    private Instant parseScheduledAtOrNull(String date, String hhmm) {
        try {
            return parseScheduledAtInstant(date, hhmm);
        } catch (Exception e) {
            return null;
        }
    }
}
