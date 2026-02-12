package com.slack.bot.application.interactivity.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.util.json.GsonFactory;
import com.slack.api.model.view.View;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.factory.ReviewReservationTimeViewFactory;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewScheduleModalPublisher {

    private final ReviewReservationTimeViewFactory slackViews;
    private final ObjectMapper objectMapper;

    public SlackActionResponse pushCustomDatetimeModal(String metaJson, String initialDate) {
        View view = slackViews.customDatetimeModal(metaJson, initialDate);
        JsonNode normalizedView = normalizeView(view);

        return SlackActionResponse.push(normalizedView);
    }

    private JsonNode normalizeView(View view) {
        String snakeCaseJson = GsonFactory.createSnakeCase().toJson(view);

        try {
            return objectMapper.readTree(snakeCaseJson);
        } catch (IOException e) {
            throw new IllegalStateException("커스텀 시간 모달 직렬화에 실패했습니다.", e);
        }
    }
}
