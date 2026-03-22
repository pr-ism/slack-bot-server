package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.support.jackson.FailingObjectMapper;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxIdempotencyPayloadEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void source_project_team_channel을_DTO_JSON으로_인코딩한다() throws Exception {
        // given
        ReviewNotificationOutboxIdempotencyPayloadEncoder encoder =
                new ReviewNotificationOutboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actualJson = encoder.encode("SOURCE-1", 1L, "T1", "C1");

        // then
        JsonNode actual = objectMapper.readTree(actualJson);
        assertAll(
                () -> assertThat(actual.path("sourceKey").asText()).isEqualTo("SOURCE-1"),
                () -> assertThat(actual.path("projectId").asLong()).isEqualTo(1L),
                () -> assertThat(actual.path("teamId").asText()).isEqualTo("T1"),
                () -> assertThat(actual.path("channelId").asText()).isEqualTo("C1")
        );
    }

    @Test
    void null_입력은_빈문자열로_인코딩한다() throws Exception {
        // given
        ReviewNotificationOutboxIdempotencyPayloadEncoder encoder =
                new ReviewNotificationOutboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actualJson = encoder.encode(null, null, null);

        // then
        JsonNode actual = objectMapper.readTree(actualJson);
        assertAll(
                () -> assertThat(actual.path("sourceKey").asText()).isEmpty(),
                () -> assertThat(actual.path("projectId").isNull()).isTrue(),
                () -> assertThat(actual.path("teamId").asText()).isEmpty(),
                () -> assertThat(actual.path("channelId").asText()).isEmpty()
        );
    }

    @Test
    void snapshot_경로는_projectId를_null로_인코딩한다() throws Exception {
        // given
        ReviewNotificationOutboxIdempotencyPayloadEncoder encoder =
                new ReviewNotificationOutboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actualJson = encoder.encode("SOURCE-1", "T1", "C1");

        // then
        JsonNode actual = objectMapper.readTree(actualJson);
        assertAll(
                () -> assertThat(actual.path("sourceKey").asText()).isEqualTo("SOURCE-1"),
                () -> assertThat(actual.path("projectId").isNull()).isTrue(),
                () -> assertThat(actual.path("teamId").asText()).isEqualTo("T1"),
                () -> assertThat(actual.path("channelId").asText()).isEqualTo("C1")
        );
    }

    @Test
    void 직렬화_실패_시_IllegalStateException을_던진다() {
        // given
        ReviewNotificationOutboxIdempotencyPayloadEncoder encoder =
                new ReviewNotificationOutboxIdempotencyPayloadEncoder(new FailingObjectMapper());

        // when & then
        assertThatThrownBy(() -> encoder.encode("SOURCE-1", 1L, "T1", "C1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("review outbox 멱등성 payload 직렬화에 실패했습니다.");
    }
}
