package com.slack.bot.application.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.event.handler.SlackEventHandlerRegistry;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SuppressWarnings("NonAsciiCharacters")
class SlackEventServiceTest {

    @Autowired
    SlackEventService slackEventService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SlackEventHandlerRegistry slackEventHandlerRegistry;

    @Test
    void event가_없는_payload면_핸들러를_호출하지_않는다() {
        // given
        JsonNode payloadWithoutEvent = objectMapper.createObjectNode();

        // when
        slackEventService.handleEvent(payloadWithoutEvent);

        // then
        verify(slackEventHandlerRegistry, never()).handle(anyString(), any(JsonNode.class));
    }

    @Test
    void event_type에_매핑되는_핸들러를_호출한다() {
        // given
        JsonNode payload = createPayload("member_joined_channel");

        // when
        slackEventService.handleEvent(payload);

        // then
        verify(slackEventHandlerRegistry).handle("member_joined_channel", payload);
    }

    private JsonNode createPayload(String eventType) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", eventType);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("team_id", "T1");
        payload.set("event", event);
        return payload;
    }
}
