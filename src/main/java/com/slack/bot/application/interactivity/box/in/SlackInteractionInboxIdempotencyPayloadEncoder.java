package com.slack.bot.application.interactivity.box.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackInteractionInboxIdempotencyPayloadEncoder {

    private final ObjectMapper objectMapper;

    public String encodeBlockAction(String payloadJson) {
        JsonNode payload = parsePayload(payloadJson);

        if (payload == null) {
            return payloadJson;
        }

        JsonNode action = firstAction(payload);

        return encode(
                new BlockActionIdempotencySource(
                        resolveTeamId(payload),
                        payload.path("channel").path("id").asText(""),
                        payload.path("user").path("id").asText(""),
                        action.path("action_id").asText(""),
                        action.path("value").asText(""),
                        readActionTimestamp(payload, action),
                        payload.path("view").path("id").asText("")
                ),
                payloadJson
        );
    }

    public String encodeViewSubmission(String payloadJson) {
        JsonNode payload = parsePayload(payloadJson);

        if (payload == null) {
            return payloadJson;
        }

        JsonNode view = payload.path("view");

        return encode(
                new ViewSubmissionIdempotencySource(
                        resolveTeamId(payload),
                        payload.path("user").path("id").asText(""),
                        view.path("id").asText(""),
                        view.path("callback_id").asText(""),
                        view.path("private_metadata").asText("")
                ),
                payloadJson
        );
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Slack 인터랙션 payload 파싱에 실패했습니다. payloadLength={}",
                    payloadJson.length(),
                    exception
            );
            return null;
        }
    }

    private JsonNode firstAction(JsonNode payload) {
        JsonNode actions = payload.path("actions");

        if (!actions.isArray() || actions.isEmpty()) {
            return payload.path("action");
        }

        return actions.get(0);
    }

    private String readActionTimestamp(JsonNode payload, JsonNode action) {
        String payloadActionTimestamp = payload.path("action_ts").asText("");
        if (!payloadActionTimestamp.isBlank()) {
            return payloadActionTimestamp;
        }

        return action.path("action_ts").asText("");
    }

    private String resolveTeamId(JsonNode payload) {
        String teamId = payload.path("team_id").asText("");
        if (!teamId.isBlank()) {
            return teamId;
        }

        teamId = payload.path("team").path("id").asText("");
        if (!teamId.isBlank()) {
            return teamId;
        }

        return payload.path("user").path("team_id").asText("");
    }

    private String encode(Object source, String fallbackJson) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Slack 인터랙션 멱등성 payload 직렬화에 실패했습니다. sourceType={}",
                    source.getClass().getSimpleName(),
                    exception
            );
            return fallbackJson;
        }
    }

    private record BlockActionIdempotencySource(
            String teamId,
            String channelId,
            String userId,
            String actionId,
            String actionValue,
            String actionTimestamp,
            String viewId
    ) {
    }

    private record ViewSubmissionIdempotencySource(
            String teamId,
            String userId,
            String viewId,
            String callbackId,
            String privateMetadata
    ) {
    }
}
