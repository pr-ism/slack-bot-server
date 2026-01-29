package com.slack.bot.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.handler.SlackEventHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackEventService {

    private final SlackEventHandlerRegistry handlerRegistry;

    public void handleEvent(JsonNode payload) {
        JsonNode event = payload.get("event");

        if (event == null) {
            log.info("이벤트 payload를 찾을 수 없습니다.");
            return;
        }

        String eventType = event.path("type")
                                .asText();

        handlerRegistry.handle(eventType, payload);
    }
}
