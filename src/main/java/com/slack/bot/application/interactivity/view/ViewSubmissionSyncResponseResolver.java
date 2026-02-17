package com.slack.bot.application.interactivity.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionSyncResultDto;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ViewSubmissionSyncResponseResolver {

    private static final String OPTION_CUSTOM = "custom";
    private static final String OPTION_NOW = "now";
    private static final String INVALID_TIME_MESSAGE = "리뷰 시작 시간 값이 올바르지 않습니다.";

    private final Clock clock;
    private final ReviewTimeValidator reviewTimeValidator;
    private final ReviewScheduleModalPublisher reviewScheduleModalPublisher;

    public ViewSubmissionSyncResultDto resolve(JsonNode payload) {
        ViewCallbackId callbackId = ViewCallbackId.from(readCallbackId(payload));

        if (callbackId.isReviewTimeSubmit()) {
            return resolveDefaultSubmit(payload);
        }
        if (callbackId.isReviewTimeCustomSubmit()) {
            return resolveCustomSubmit(payload);
        }

        return ViewSubmissionSyncResultDto.noEnqueue(SlackActionResponse.empty());
    }

    private ViewSubmissionSyncResultDto resolveDefaultSubmit(JsonNode payload) {
        String selected = readSelectedOption(payload);

        if (selected == null || selected.isBlank()) {
            return ViewSubmissionSyncResultDto.noEnqueue(SlackActionResponse.empty());
        }
        if (OPTION_CUSTOM.equals(selected)) {
            String today = LocalDate.now(clock).toString();
            String metaJson = readMetaJson(payload);
            SlackActionResponse response = reviewScheduleModalPublisher.pushCustomDatetimeModal(metaJson, today);
            return ViewSubmissionSyncResultDto.noEnqueue(response);
        }
        if (OPTION_NOW.equals(selected)) {
            return ViewSubmissionSyncResultDto.enqueue(SlackActionResponse.clear());
        }

        Optional<Long> minutes = parseMinutes(selected);

        if (minutes.isEmpty()) {
            return ViewSubmissionSyncResultDto.noEnqueue(SlackActionResponse.errors(Map.of("time_block", INVALID_TIME_MESSAGE)));
        }

        return ViewSubmissionSyncResultDto.enqueue(SlackActionResponse.clear());
    }

    private ViewSubmissionSyncResultDto resolveCustomSubmit(JsonNode payload) {
        String selectedDate = readSelectedDate(payload);
        String timeText = readTimeText(payload);
        Map<String, String> errors = reviewTimeValidator.validateCustomDateTime(selectedDate, timeText);

        if (!errors.isEmpty()) {
            return ViewSubmissionSyncResultDto.noEnqueue(SlackActionResponse.errors(errors));
        }

        return ViewSubmissionSyncResultDto.enqueue(SlackActionResponse.clear());
    }

    private Optional<Long> parseMinutes(String selected) {
        try {
            return Optional.of(Long.parseLong(selected));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String readCallbackId(JsonNode payload) {
        return payload.path("view")
                      .path("callback_id")
                      .asText();
    }

    private String readMetaJson(JsonNode payload) {
        return payload.path("view")
                      .path("private_metadata")
                      .asText();
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
}
