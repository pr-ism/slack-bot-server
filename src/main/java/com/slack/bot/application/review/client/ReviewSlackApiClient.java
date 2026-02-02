package com.slack.bot.application.review.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.client.exception.ReviewSlackApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ReviewSlackApiClient {

    private static final int ERROR_BODY_MAX_LENGTH = 500;

    private final RestClient slackClient;
    private final ObjectMapper objectMapper;

    public void sendBlockMessage(
            String token,
            String channelId,
            JsonNode blocks,
            JsonNode attachments,
            String fallbackText
    ) {
        Map<String, Object> body = new HashMap<>();

        body.put("channel", channelId);
        body.put("blocks", blocks);

        if (fallbackText != null) {
            body.put("text", fallbackText);
        }
        if (attachments != null) {
            body.put("attachments", attachments);
        }

        postToSlack(token, body);
    }

    private void postToSlack(String token, Object body) {
        String authorization = "Bearer " + token;
        String responseBody = slackClient.post()
                                         .uri("chat.postMessage")
                                         .header("Authorization", authorization)
                                         .contentType(MediaType.APPLICATION_JSON)
                                         .body(body)
                                         .retrieve()
                                         .onStatus(status ->
                                                 status.isError(),
                                                 this.slackApiErrorHandler("chat.postMessage")
                                         )
                                         .body(String.class);

        validateResponseBody(responseBody);
    }

    private RestClient.ResponseSpec.ErrorHandler slackApiErrorHandler(String apiName) {
        return (request, responseError) -> {
            String message = buildErrorMessage(apiName, responseError);

            throw new ReviewSlackApiException(message);
        };
    }

    private String buildErrorMessage(
            String apiName,
            ClientHttpResponse response
    ) throws IOException {
        String message = "Slack API 요청 실패 (%s). status=%d".formatted(
                apiName, response.getStatusCode().value()
        );

        try {
            String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

            if (!responseBody.isBlank()) {
                message += ", body=" + truncateBody(responseBody);
            }
        } catch (IOException ignored) {
            // body read 실패 때문에 원래 에러 메시지까지 유실되는 것을 방지
        }

        return message;
    }

    private String truncateBody(String responseBody) {
        if (responseBody.length() <= ERROR_BODY_MAX_LENGTH) {
            return responseBody;
        }

        return responseBody.substring(0, ERROR_BODY_MAX_LENGTH) + "...(truncated)";
    }

    private void validateResponseBody(String responseBody) {
        if (responseBody == null) {
            throw new ReviewSlackApiException("Slack API 응답이 비어 있습니다.");
        }

        JsonNode root = readResponse(responseBody);

        if (!root.path("ok").asBoolean()) {
            String error = root.path("error").asText();
            throw new ReviewSlackApiException("Slack API 요청 실패: " + error);
        }
    }

    private JsonNode readResponse(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new ReviewSlackApiException("Slack API 응답 파싱 실패", e);
        }
    }
}
