package com.slack.bot.global.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
public class SlackAppender extends AppenderBase<ILoggingEvent> {

    private static final String PROFILE = System.getenv("PROFILE");
    private static final String WEB_HOOK = System.getenv("WEB_HOOK");
    private static final String INVALID_PROFILE = "INVALID_PROFILE";
    private static final String EXCEPTION_FORMAT = "전송 실패 : %s";
    private static final String SLACK_MESSAGE_FORMAT = "에러가 발생했습니다.\n```%s %s %s [%s] - %s```";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    protected void append(ILoggingEvent eventObject) {
        Map<String, Object> message = createMessage(eventObject);

        try {
            sendMessage(message);
        } catch (Exception e) {
            log.warn(String.format(EXCEPTION_FORMAT, e));
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
                                                "value", PROFILE == null ? INVALID_PROFILE : PROFILE,
                                                "short", false
                                        )
                                ),
                                "ts", eventObject.getTimeStamp()
                        )
                )
        );
    }

    private String createText(ILoggingEvent eventObject) {
        return String.format(
                SLACK_MESSAGE_FORMAT,
                DATE_FORMAT.format(eventObject.getTimeStamp()),
                eventObject.getLevel(),
                eventObject.getThreadName(),
                eventObject.getLoggerName(),
                eventObject.getFormattedMessage()
        );
    }

    private void sendMessage(Map<String, Object> message) {
        RestClient restClient = RestClient.create();

        restClient.post()
                  .uri(WEB_HOOK)
                  .body(message)
                  .retrieve()
                  .onStatus(
                          httpStatusCode -> httpStatusCode.is4xxClientError(),
                          (request, response) -> log.warn(String.format(EXCEPTION_FORMAT, response.getBody()))
                  )
                  .onStatus(
                          httpStatusCode -> httpStatusCode.is5xxServerError(),
                          (request, response) -> log.warn(String.format(EXCEPTION_FORMAT, response.getBody()))
                  );
    }
}
