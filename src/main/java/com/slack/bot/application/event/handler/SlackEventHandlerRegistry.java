package com.slack.bot.application.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;

public final class SlackEventHandlerRegistry {

    private final Map<String, SlackEventHandler> handlers;

    public static SlackEventHandlerRegistry of(Map<String, SlackEventHandler> handlers) {
        return new SlackEventHandlerRegistry(Collections.unmodifiableMap(handlers));
    }

    private SlackEventHandlerRegistry(Map<String, SlackEventHandler> handlers) {
        this.handlers = handlers;
    }

    public void handle(String eventType, JsonNode payload) {
        SlackEventHandler handler = this.handlers.get(eventType);

        if (handler == null) {
            return;
        }

        handler.handle(payload);
    }
}
