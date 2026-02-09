package com.slack.bot.application.interactivity.block.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.application.interactivity.block.dto.BlockActionContextDto;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BlockActionContextFactoryTest {

    @Autowired
    BlockActionContextFactory blockActionContextFactory;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 유효한_블록_액션_요청이면_컨텍스트를_생성한다() {
        // given
        JsonNode payload = payload("T1", "C1", "U1", action("cancel_review_reservation", "100"));

        // when
        Optional<BlockActionContextDto> actual = blockActionContextFactory.create(payload);

        // then
        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().teamId()).isEqualTo("T1"),
                () -> assertThat(actual.get().channelId()).isEqualTo("C1"),
                () -> assertThat(actual.get().slackUserId()).isEqualTo("U1"),
                () -> assertThat(actual.get().actionId()).isEqualTo("cancel_review_reservation"),
                () -> assertThat(actual.get().botToken()).isEqualTo("xoxb-test-token")
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 액션_ID가_없으면_빈_값을_반환한다() {
        // given
        JsonNode payload = payload("T1", "C1", "U1", objectMapper.createObjectNode());

        // when
        Optional<BlockActionContextDto> actual = blockActionContextFactory.create(payload);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void actions_배열이_없으면_빈_값을_반환한다() {
        // given
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        // when
        Optional<BlockActionContextDto> actual = blockActionContextFactory.create(payload);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void team_ID가_없으면_빈_값을_반환한다() {
        // given
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("actions", objectMapper.createArrayNode().add(action("cancel_review_reservation", "100")));

        // when
        Optional<BlockActionContextDto> actual = blockActionContextFactory.create(payload);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void 워크스페이스가_없으면_예외가_발생한다() {
        // given
        JsonNode payload = payload("T404", "C1", "U1", action("cancel_review_reservation", "100"));

        // when & then
        assertThatThrownBy(() -> blockActionContextFactory.create(payload))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    private JsonNode payload(String teamId, String channelId, String userId, JsonNode action) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode actions = objectMapper.createArrayNode();

        payload.set("team", objectMapper.createObjectNode().put("id", teamId));
        payload.set("channel", objectMapper.createObjectNode().put("id", channelId));
        payload.set("user", objectMapper.createObjectNode().put("id", userId));
        actions.add(action);
        payload.set("actions", actions);

        return payload;
    }

    private JsonNode action(String actionId, String value) {
        return objectMapper.createObjectNode()
                           .put("action_id", actionId)
                           .put("value", value);
    }
}
