package com.slack.bot.application.event.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.client.exception.SlackChatRequestException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SlackEventApiClient {

    private static final String SLACK_API_ERROR_MESSAGE = "Slack API 응답 오류. Error: %s";

    private final RestClient slackClient;

    public void sendEphemeralMessage(String token, String channelId, String targetUserId, String text) {
        Map<String, Object> body = createEphemeralMessageBody(channelId, targetUserId, text);

        sendPostRequest(token, "chat.postEphemeral", body);
    }

    public void sendMessage(String token, String channelId, String text) {
        Map<String, Object> body = createMessageBody(channelId, text);

        sendPostRequest(token, "chat.postMessage", body);
    }

    private void sendPostRequest(String token, String uri, Map<String, Object> body) {
        String authorization = createAuthorizationHeader(token);
        JsonNode response = slackClient.post()
                                       .uri(uri)
                                       .header("Authorization", authorization)
                                       .contentType(MediaType.APPLICATION_JSON)
                                       .body(body)
                                       .retrieve()
                                       .onStatus(status -> status.isError(), (request, resp) -> {
                                           String message = "Slack API HTTP 요청 실패. Status: %s".formatted(
                                                   resp.getStatusCode()
                                           );

                                           throw new SlackChatRequestException(message);
                                       })
                                       .body(JsonNode.class);

        validateResponse(response);
    }

    private void validateResponse(JsonNode response) {
        if (response == null || !response.path("ok").asBoolean()) {
            String errorMessage = "슬랙 봇으로 채팅을 입력하지 못했습니다.";

            if (response != null && response.has("error")) {
                errorMessage = response.get("error").asText();
            }

            throw new SlackChatRequestException(
                    String.format(SLACK_API_ERROR_MESSAGE, errorMessage)
            );
        }
    }

    private String createAuthorizationHeader(String token) {
        return "Bearer " + token;
    }

    private Map<String, Object> createEphemeralMessageBody(String channelId, String targetUserId, String text) {
        Map<String, Object> body = new HashMap<>();

        body.put("channel", channelId);
        body.put("user", targetUserId);
        body.put("text", text);
        return body;
    }

    private Map<String, Object> createMessageBody(String channelId, String text) {
        Map<String, Object> body = new HashMap<>();

        body.put("channel", channelId);
        body.put("text", text);
        return body;
    }
}
