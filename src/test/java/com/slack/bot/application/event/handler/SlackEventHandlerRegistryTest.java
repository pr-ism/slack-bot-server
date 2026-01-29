package com.slack.bot.application.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.event.handler.spy.SpySlackEventHandler;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackEventHandlerRegistryTest {

    ObjectMapper objectMapper =  new ObjectMapper();

    @Test
    void 등록된_이벤트_타입이면_해당_핸들러를_호출한다() {
        // given
        SpySlackEventHandler handler = new SpySlackEventHandler();
        SlackEventHandlerRegistry registry = SlackEventHandlerRegistry.of(
                Map.of("member_joined_channel", handler)
        );

        JsonNode payload = createPayload();

        // when
        registry.handle("member_joined_channel", payload);

        // then
        assertAll(
                () -> assertThat(handler.getCallCount()).isEqualTo(1),
                () -> assertThat(handler.getLastPayload()).isSameAs(payload)
        );
    }

    @Test
    void 등록되지_않은_이벤트_타입이면_아무_동작도_하지_않는다() {
        // given
        SpySlackEventHandler handler = new SpySlackEventHandler();
        SlackEventHandlerRegistry registry = SlackEventHandlerRegistry.of(
                Map.of("member_joined_channel", handler)
        );

        // when
        registry.handle("app_uninstalled", createPayload());

        // then
        assertThat(handler.getCallCount()).isZero();
    }

    private JsonNode createPayload() {
        return objectMapper.createObjectNode()
                           .put("event_type", "test");
    }
}
