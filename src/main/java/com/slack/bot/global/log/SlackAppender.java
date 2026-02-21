package com.slack.bot.global.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class SlackAppender extends AppenderBase<ILoggingEvent> {

    private static final String INVALID_PROFILE = "INVALID_PROFILE";
    private static final String EXCEPTION_FORMAT = "전송 실패 : %s";
    private static final String SLACK_MESSAGE_FORMAT = "에러가 발생했습니다.\n```%s %s %s [%s] - %s```";
    private static final String INVALID_WEB_HOOK_MESSAGE = "WEB_HOOK 환경변수가 설정되지 않았습니다. SlackAppender를 비활성화합니다.";

    private final RestClient restClient;
    private final DateTimeFormatter dateFormatter;
    private final String webHook;
    private final String profile;

    @Override
    public void start() {
        if (webHook == null) {
            addError(INVALID_WEB_HOOK_MESSAGE);
            return;
        }
        if (webHook.isBlank()) {
            addError(INVALID_WEB_HOOK_MESSAGE);
            return;
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        Map<String, Object> message = createMessage(eventObject);

        try {
            sendMessage(message);
        } catch (Exception e) {
            String errorMessage = resolveExceptionMessage(e);
            String formattedMessage = formatException(errorMessage);

            addWarn(formattedMessage, e);
        }
    }

    private Map<String, Object> createMessage(ILoggingEvent eventObject) {
        String text = createText(eventObject);

        return Map.of(
                "attachments", List.of(
                        Map.of(
                                "fallback", "에러가 발생했습니다.",
                                "color", "#2EB886",
                                "author_name", "PRism - Slack Server",
                                "text", text,
                                "fields", List.of(
                                        Map.of(
                                                "title", "서버 환경",
                                                "value", resolveProfile(),
                                                "short", false
                                        )
                                ),
                                "ts", eventObject.getTimeStamp()
                        )
                )
        );
    }

    private String createText(ILoggingEvent eventObject) {
        String formattedTimestamp = formatTimestamp(eventObject.getTimeStamp());
        return String.format(
                SLACK_MESSAGE_FORMAT,
                formattedTimestamp,
                eventObject.getLevel(),
                eventObject.getThreadName(),
                eventObject.getLoggerName(),
                eventObject.getFormattedMessage()
        );
    }

    private void sendMessage(Map<String, Object> message) {
        restClient.post()
                  .uri(webHook)
                  .body(message)
                  .retrieve()
                  .onStatus(
                          httpStatusCode -> httpStatusCode.is4xxClientError(),
                          (request, response) -> {
                              String responseBody = extractResponseBody(response);
                              String formattedMessage = formatException(responseBody);
                              addWarn(formattedMessage);
                          }
                  )
                  .onStatus(
                          httpStatusCode -> httpStatusCode.is5xxServerError(),
                          (request, response) -> {
                              String responseBody = extractResponseBody(response);
                              String formattedMessage = formatException(responseBody);
                              addWarn(formattedMessage);
                          }
                  )
                  .toBodilessEntity();
    }

    private String extractResponseBody(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            String responseBody = readResponseBody(body);
            return response.getStatusCode() + " " + responseBody;
        } catch (IOException e) {
            addWarn("응답 본문 읽기에 실패했습니다.", e);
            return "응답 본문 읽기 실패";
        }
    }

    private String resolveProfile() {
        if (profile == null) {
            return INVALID_PROFILE;
        }
        if (profile.isBlank()) {
            return INVALID_PROFILE;
        }
        return profile;
    }

    private String resolveExceptionMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return exception.toString();
        }
        return message;
    }

    private String formatException(String errorMessage) {
        return String.format(EXCEPTION_FORMAT, errorMessage);
    }

    private String readResponseBody(InputStream body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String formatTimestamp(long epochMilli) {
        Instant instant = Instant.ofEpochMilli(epochMilli);
        return dateFormatter.format(instant);
    }
}
