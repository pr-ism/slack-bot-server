package com.slack.bot.application.interactivity.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.block.dto.BlockActionHandlingResultDto;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BlockActionRouterTest {

    @Autowired
    BlockActionRouter blockActionRouter;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 알수없는_액션_타입이면_빈_값을_반환한다() {
        // given
        JsonNode payload = payload("unknown-action", "100");

        // when
        Optional<BlockActionHandlingResultDto> actual = blockActionRouter.route(payload);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 예약_취소_액션이면_처리_결과를_반환한다() {
        // given
        JsonNode payload = payload(BlockActionType.CANCEL_REVIEW_RESERVATION.value(), "100");

        // when
        Optional<BlockActionHandlingResultDto> actual = blockActionRouter.route(payload);

        // then
        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().context().actionId()).isEqualTo(BlockActionType.CANCEL_REVIEW_RESERVATION.value()),
                () -> assertThat(actual.get().outcome().cancelledReservation()).isNotNull(),
                () -> assertThat(actual.get().outcome().cancelledReservation().getId()).isEqualTo(100L)
        );
    }

    private JsonNode payload(String actionId, String value) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ArrayNode actions = objectMapper.createArrayNode();
        actions.add(objectMapper.createObjectNode()
                .put("action_id", actionId)
                .put("value", value));
        payload.set("actions", actions);

        return payload;
    }
}
