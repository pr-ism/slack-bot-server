package com.slack.bot.application.interactivity.client;

import com.slack.bot.application.interactivity.client.dto.response.SlackConversationsOpenResponse;
import com.slack.bot.application.interactivity.client.dto.response.SlackConversationsOpenResponse.SlackChannel;
import com.slack.bot.application.interactivity.client.exception.SlackDmException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SlackDirectMessageClient {

    private static final int ERROR_BODY_MAX_LENGTH = 500;

    private final RestClient slackClient;

    public void send(String botToken, String userId, String message) {
        validateInputs(botToken, userId, message);

        String channelId = openDirectMessageChannel(botToken, userId);

        postDirectMessage(botToken, channelId, message);
    }

    private void validateInputs(String botToken, String userId, String message) {
        if (botToken == null || botToken.isBlank()) {
            throw new SlackDmException("Slack DM 전송 실패: botToken이 필요합니다.");
        }
        if (userId == null || userId.isBlank()) {
            throw new SlackDmException("Slack DM 전송 실패: 사용자 ID가 필요합니다.");
        }
        if (message == null || message.isBlank()) {
            throw new SlackDmException("Slack DM 전송 실패: 메시지가 비어 있습니다.");
        }
    }

    private String openDirectMessageChannel(String botToken, String userId) {
        SlackConversationsOpenResponse response = slackClient.post()
                         .uri("conversations.open")
                         .header("Authorization", "Bearer " + botToken)
                         .contentType(MediaType.APPLICATION_JSON)
                         .body(Map.of("users", userId))
                         .retrieve()
                         .onStatus(status -> status.isError(), slackApiErrorHandler("conversations.open"))
                         .body(SlackConversationsOpenResponse.class);

        return extractChannelId(response);
    }

    private void postDirectMessage(String botToken, String channelId, String message) {
        slackClient.post()
                   .uri("chat.postMessage")
                   .header("Authorization", "Bearer " + botToken)
                   .contentType(MediaType.APPLICATION_JSON)
                   .body(Map.of("channel", channelId, "text", message))
                   .retrieve()
                   .onStatus(status -> status.isError(), slackApiErrorHandler("chat.postMessage"))
                   .toBodilessEntity();
    }

    private RestClient.ResponseSpec.ErrorHandler slackApiErrorHandler(String apiName) {
        return (request, responseError) -> {
            StringBuilder messageBuilder = new StringBuilder()
                    .append("Slack API 요청 실패 (")
                    .append(apiName)
                    .append("). status=")
                    .append(responseError.getStatusCode().value());

            String responseBody = readResponseBody(responseError);

            if (responseBody != null && !responseBody.isBlank()) {
                messageBuilder.append(", body=")
                              .append(truncateBody(responseBody));
            }

            throw new SlackDmException(messageBuilder.toString());
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
            return null;
        }
    }

    private String extractChannelId(SlackConversationsOpenResponse response) {
        if (response == null) {
            throw new SlackDmException("Slack DM 채널 열기 실패: 응답이 비어 있습니다.");
        }
        if (!response.ok()) {
            String error = resolveError(response);

            throw new SlackDmException("Slack DM 채널 열기 실패: " + error);
        }

        SlackChannel channel = response.channel();

        if (channel == null || channel.id() == null) {
            throw new SlackDmException("Slack DM 채널 열기 실패: 채널 ID가 없습니다.");
        }

        return channel.id();
    }

    private String resolveError(SlackConversationsOpenResponse response) {
        String error = response.error();

        if (error == null || error.isBlank()) {
            return "알 수 없는 오류";
        }

        return error;
    }
}
