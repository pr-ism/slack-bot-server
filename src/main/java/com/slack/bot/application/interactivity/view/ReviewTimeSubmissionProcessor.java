package com.slack.bot.application.interactivity.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.workflow.ReviewReservationWorkflow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewTimeSubmissionProcessor {

    private static final String OPTION_CUSTOM = "custom";
    private static final String OPTION_NOW = "now";
    private static final String INVALID_TIME_MESSAGE = "리뷰 시작 시간 값이 올바르지 않습니다.";

    private final Clock clock;
    private final ReviewTimeValidator reviewTimeValidator;
    private final ReviewReservationWorkflow reservationWorkflow;
    private final ReviewScheduleModalPublisher reviewScheduleModalPublisher;

    public SlackActionResponse handleDefaultTimeSubmit(
            JsonNode payload,
            String metaJson,
            ReviewScheduleMetaDto meta,
            String reviewerId,
            String token
    ) {
        String selected = readSelectedOption(payload);

        if (selected == null || selected.isBlank()) {
            return SlackActionResponse.empty();
        }

        SelectionContext context = new SelectionContext(metaJson, meta, reviewerId, token);

        return dispatchSelection(selected, context);
    }

    public SlackActionResponse handleCustomTimeSubmit(
            JsonNode payload,
            ReviewScheduleMetaDto meta,
            String reviewerId,
            String token
    ) {
        String selectedDate = readSelectedDate(payload);
        String timeText = readTimeText(payload);
        Map<String, String> errors = reviewTimeValidator.validateCustomDateTime(selectedDate, timeText);

        if (!errors.isEmpty()) {
            return SlackActionResponse.errors(errors);
        }

        String normalized = reviewTimeValidator.normalizeTimeOrNull(timeText);
        Instant scheduledAt = reviewTimeValidator.parseScheduledAtInstant(selectedDate, normalized);

        return reservationWorkflow.reserveReview(meta, reviewerId, token, scheduledAt);
    }

    private SlackActionResponse dispatchSelection(String selected, SelectionContext context) {
        if (OPTION_CUSTOM.equals(selected)) {
            String today = LocalDate.now(clock).toString();

            return reviewScheduleModalPublisher.pushCustomDatetimeModal(context.metaJson(), today);
        }

        if (OPTION_NOW.equals(selected)) {
            return reservationWorkflow.reserveReview(
                    context.meta(),
                    context.reviewerId(),
                    context.token(),
                    Instant.now(clock)
            );
        }

        return parseMinutes(selected)
                .map(minutes -> Instant.now(clock).plus(Duration.ofMinutes(minutes)))
                .map(scheduledAt -> reservationWorkflow.reserveReview(
                        context.meta(),
                        context.reviewerId(),
                        context.token(),
                        scheduledAt
                ))
                .orElseGet(() -> invalidTimeSelectionResponse());
    }

    private static Optional<Long> parseMinutes(String selected) {
        try {
            return Optional.of(Long.parseLong(selected));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String readSelectedDate(JsonNode payload) {
        return payload.path("view")
                      .path("state").path("values")
                      .path("date_block").path("date_action")
                      .path("selected_date")
                      .asText();
    }

    private String readTimeText(JsonNode payload) {
        return payload.path("view")
                      .path("state").path("values")
                      .path("time_block").path("time_action")
                      .path("value")
                      .asText();
    }

    private String readSelectedOption(JsonNode payload) {
        return payload.path("view")
                      .path("state").path("values")
                      .path("time_block").path("time_action")
                      .path("selected_option").path("value")
                      .asText();
    }

    private SlackActionResponse invalidTimeSelectionResponse() {
        return SlackActionResponse.errors(Map.of("time_block", INVALID_TIME_MESSAGE));
    }

    private record SelectionContext(
            String metaJson,
            ReviewScheduleMetaDto meta,
            String reviewerId,
            String token
    ) {
    }
}
