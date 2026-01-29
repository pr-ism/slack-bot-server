package com.slack.bot.application.event.handler;

import com.fasterxml.jackson.databind.JsonNode;

public interface SlackEventHandler {

    void handle(JsonNode payload);
}
