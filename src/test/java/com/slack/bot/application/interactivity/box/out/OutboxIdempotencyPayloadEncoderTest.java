package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.out.exception.OutboxMessageTypeRequiredException;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.support.jackson.FailingObjectMapper;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxIdempotencyPayloadEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 인코더_인스턴스_생성이_가능하다() {
        // when
        OutboxIdempotencyPayloadEncoder encoder = new OutboxIdempotencyPayloadEncoder(objectMapper);

        // then
        assertAll(
                () -> assertThat(encoder).isNotNull()
        );
    }

    @Test
    void 입력_값을_DTO_JSON으로_인코딩한다() throws Exception {
        // given
        OutboxIdempotencyPayloadEncoder encoder = new OutboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actual = encoder.encode(
                "SRC",
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                "T1",
                "C1",
                null
        );

        // then
        JsonNode sourceNode = objectMapper.readTree(actual);
        assertAll(
                () -> assertThat(sourceNode.path("sourceKey").asText()).isEqualTo("SRC"),
                () -> assertThat(sourceNode.path("messageType").asText()).isEqualTo("CHANNEL_BLOCKS"),
                () -> assertThat(sourceNode.path("teamId").asText()).isEqualTo("T1"),
                () -> assertThat(sourceNode.path("channelId").asText()).isEqualTo("C1"),
                () -> assertThat(sourceNode.path("userId").asText()).isEmpty()
        );
    }

    @Test
    void message_type이_null이면_custom_exception을_던진다() {
        // given
        OutboxIdempotencyPayloadEncoder encoder = new OutboxIdempotencyPayloadEncoder(objectMapper);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> encoder.encode("SRC", null, "T1", "C1", "U1"))
                        .isInstanceOf(OutboxMessageTypeRequiredException.class)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("messageType은 비어 있을 수 없습니다.")
        );
    }

    @Test
    void 직렬화_실패_시_IllegalStateException을_던진다() {
        // given
        OutboxIdempotencyPayloadEncoder encoder = new OutboxIdempotencyPayloadEncoder(new FailingObjectMapper());

        // when & then
        assertThatThrownBy(() -> encoder.encode(
                "SRC",
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                "T1",
                "C1",
                "U1"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("직렬화에 실패");
    }
}
