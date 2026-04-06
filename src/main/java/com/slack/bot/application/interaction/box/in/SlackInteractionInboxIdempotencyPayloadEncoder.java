package com.slack.bot.application.interaction.box.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
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

        if (payload.isMissingNode()) {
            return payloadJson;
        }

        JsonNode action = firstAction(payload);

        return encode(
                new BlockActionIdempotencySource(
                        resolveTeamId(payload),
                        resolveText(payload.path("channel").path("id")),
                        resolveText(payload.path("user").path("id")),
                        resolveText(action.path("action_id")),
                        resolveText(action.path("value")),
                        resolveText(payload.path("view").path("id"))
                ),
                payloadJson
        );
    }

    public String encodeViewSubmission(String payloadJson) {
        JsonNode payload = parsePayload(payloadJson);

        if (payload.isMissingNode()) {
            return payloadJson;
        }

        JsonNode view = payload.path("view");

        return encode(
                new ViewSubmissionIdempotencySource(
                        resolveTeamId(payload),
                        resolveText(payload.path("user").path("id")),
                        resolveText(view.path("id")),
                        resolveText(view.path("callback_id")),
                        resolveText(view.path("private_metadata"))
                ),
                payloadJson
        );
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return MissingNode.getInstance();
        }

        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Slack 인터랙션 payload 파싱에 실패했습니다. payloadLength={}",
                    payloadJson.length(),
                    exception
            );
            return MissingNode.getInstance();
        }
    }

    private JsonNode firstAction(JsonNode payload) {
        JsonNode actions = payload.path("actions");

        if (!actions.isArray() || actions.isEmpty()) {
            return payload.path("action");
        }

        return actions.get(0);
    }

    private String resolveTeamId(JsonNode payload) {
        String teamId = resolveText(payload.path("team_id"));
        if (!teamId.isBlank()) {
            return teamId;
        }

        teamId = resolveText(payload.path("team").path("id"));
        if (!teamId.isBlank()) {
            return teamId;
        }

        return resolveText(payload.path("user").path("team_id"));
    }

    private String resolveText(JsonNode node) {
        if (node == null) {
            return "";
        }
        if (node.isMissingNode() || node.isNull() || !node.isValueNode()) {
            return "";
        }

        return node.asText("");
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
