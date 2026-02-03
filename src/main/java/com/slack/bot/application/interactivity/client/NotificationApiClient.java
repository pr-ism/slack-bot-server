package com.slack.bot.application.interactivity.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class NotificationApiClient {

    private static final int ERROR_BODY_MAX_LENGTH = 500;

    private final RestClient slackClient;

    public void sendEphemeralMessage(String token, String channelId, String targetUserId, String text) {
        Map<String, String> body = buildEphemeralTextBody(channelId, targetUserId, text);
        JsonNode response = postForJson("chat.postEphemeral", token, body);

        ensureOk(response, "슬랙 봇 메시지 전송 실패: 에페메랄 메시지 전송 실패");
    }

    public void sendEphemeralBlockMessage(String token, String channelId, String targetUserId, Object blocks, String text) {
        Map<String, Object> body = buildEphemeralBlockBody(channelId, targetUserId, blocks, text);
        JsonNode response = postForJson("chat.postEphemeral", token, body);

        ensureOk(response, "슬랙 봇 메시지 전송 실패: 에페메랄 블록 메시지 전송 실패");
    }

    public void sendMessage(String token, String channelId, String text) {
        Map<String, String> body = buildMessageBody(channelId, text);
        JsonNode response = postForJson("chat.postMessage", token, body);

        ensureOk(response, "슬랙 봇 메시지 전송 실패: 메시지 전송 실패");
    }

    public void sendBlockMessage(String token, String channelId, Object blocks, String text) {
        Map<String, Object> body = buildBlockMessageBody(channelId, blocks, text);
        JsonNode response = postForJson("chat.postMessage", token, body);

        ensureOk(response, "슬랙 봇 메시지 전송 실패: 블록 메시지 전송 실패");
    }

    public String openDirectMessageChannel(String token, String userId) {
        Map<String, Object> body = buildOpenConversationBody(userId);
        JsonNode response = postForJson("conversations.open", token, body);

        ensureOk(response, "슬랙 봇 메시지 전송 실패: 직접 메시지 채널 열기 실패");

        JsonNode channelId = response.path("channel").path("id");

        validateChannelId(channelId);
        return channelId.asText();
    }

    private void validateChannelId(JsonNode channelId) {
        if (channelId.isMissingNode() || channelId.asText().isBlank()) {
            throw new SlackBotMessageDispatchException("슬랙 봇 메시지 전송 실패: 채널 ID가 없습니다.");
        }
    }

    private JsonNode postForJson(String apiName, String token, Map<String, ?> body) {
        String authorization = "Bearer " + token;

        return slackClient.post()
                          .uri(apiName)
                          .header("Authorization", authorization)
                          .contentType(MediaType.APPLICATION_JSON)
                          .body(body)
                          .retrieve()
                          .onStatus(status -> status.isError(), slackApiErrorHandler(apiName))
                          .body(JsonNode.class);
    }

    private void ensureOk(JsonNode response, String failureMessage) {
        if (response == null) {
            throw new SlackBotMessageDispatchException(failureMessage + " - 응답이 비어 있습니다.");
        }
        if (!response.path("ok").asBoolean(false)) {
            String error = response.path("error").asText("알 수 없는 오류");
            throw new SlackBotMessageDispatchException(failureMessage + " - " + error);
        }
    }

    private RestClient.ResponseSpec.ErrorHandler slackApiErrorHandler(String apiName) {
        return (request, responseError) -> {
            String responseBody = readResponseBody(responseError);
            StringBuilder messageBuilder = new StringBuilder()
                    .append("슬랙 봇 메시지 전송 실패: Slack API 요청 실패 (")
                    .append(apiName)
                    .append("). 상태코드=")
                    .append(responseError.getStatusCode().value());
            if (!responseBody.isBlank()) {
                messageBuilder.append(", 응답본문=")
                        .append(truncateBody(responseBody));
            }
            throw new SlackBotMessageDispatchException(messageBuilder.toString());
        };
    }

    private String truncateBody(String responseBody) {
        if (responseBody.length() <= ERROR_BODY_MAX_LENGTH) {
            return responseBody;
        }

        return responseBody.substring(0, ERROR_BODY_MAX_LENGTH) + "...(truncated)";
    }

    private String readResponseBody(org.springframework.http.client.ClientHttpResponse responseError) {
        try {
            return new String(responseError.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private Map<String, String> buildEphemeralTextBody(String channelId, String targetUserId, String text) {
        Map<String, String> body = new HashMap<>();

        body.put("channel", channelId);
        body.put("user", targetUserId);
        body.put("text", text);
        return body;
    }

    private Map<String, Object> buildEphemeralBlockBody(String channelId, String targetUserId, Object blocks, String text) {
        Map<String, Object> body = new HashMap<>();

        body.put("channel", channelId);
        body.put("user", targetUserId);
        body.put("blocks", blocks);

        if (text != null && !text.isBlank()) {
            body.put("text", text);
        }

        return body;
    }

    private Map<String, String> buildMessageBody(String channelId, String text) {
        Map<String, String> body = new HashMap<>();

        body.put("channel", channelId);
        body.put("text", text);
        return body;
    }

    private Map<String, Object> buildBlockMessageBody(String channelId, Object blocks, String text) {
        Map<String, Object> body = new HashMap<>();

        body.put("channel", channelId);
        body.put("blocks", blocks);

        if (text != null && !text.isBlank()) {
            body.put("text", text);
        }

        return body;
    }

    private Map<String, Object> buildOpenConversationBody(String userId) {
        Map<String, Object> body = new HashMap<>();

        body.put("users", userId);
        return body;
    }
}
