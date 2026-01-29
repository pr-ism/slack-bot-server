package com.slack.bot.application.event.handler.spy;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.handler.SlackEventHandler;
import lombok.Getter;

@Getter
public class SpySlackEventHandler implements SlackEventHandler {

    private int callCount;
    private JsonNode lastPayload;

    @Override
    public void handle(JsonNode payload) {
        callCount++;
        lastPayload = payload;
    }
}
