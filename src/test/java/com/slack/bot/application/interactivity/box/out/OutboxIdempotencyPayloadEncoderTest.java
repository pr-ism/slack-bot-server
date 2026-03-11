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
        OutboxIdempotencyPayloadEncoder actual = new OutboxIdempotencyPayloadEncoder(objectMapper);

        // then
        assertAll(
                () -> assertThat(actual).isNotNull()
        );
    }

    @Test
    void 입력_값을_DTO_JSON으로_인코딩한다() throws Exception {
        // given
        OutboxIdempotencyPayloadEncoder actualEncoder = new OutboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actualJson = actualEncoder.encode(
                "SRC",
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                "T1",
                "C1",
                null
        );

        // then
        JsonNode actual = objectMapper.readTree(actualJson);
        assertAll(
                () -> assertThat(actual.path("sourceKey").asText()).isEqualTo("SRC"),
                () -> assertThat(actual.path("messageType").asText()).isEqualTo("CHANNEL_BLOCKS"),
                () -> assertThat(actual.path("teamId").asText()).isEqualTo("T1"),
                () -> assertThat(actual.path("channelId").asText()).isEqualTo("C1"),
                () -> assertThat(actual.path("userId").asText()).isEmpty()
        );
    }

    @Test
    void message_type이_null이면_custom_exception을_던진다() {
        // given
        OutboxIdempotencyPayloadEncoder actualEncoder = new OutboxIdempotencyPayloadEncoder(objectMapper);

        // when & then
        assertThatThrownBy(() -> actualEncoder.encode("SRC", null, "T1", "C1", "U1"))
                        .isInstanceOf(OutboxMessageTypeRequiredException.class)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("messageType은 비어 있을 수 없습니다.");
    }

    @Test
    void 직렬화_실패_시_IllegalStateException을_던진다() {
        // given
        OutboxIdempotencyPayloadEncoder actualEncoder = new OutboxIdempotencyPayloadEncoder(new FailingObjectMapper());

        // when & then
        assertThatThrownBy(() -> actualEncoder.encode(
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
