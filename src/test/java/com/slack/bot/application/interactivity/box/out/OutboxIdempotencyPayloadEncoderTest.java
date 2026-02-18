package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxIdempotencyPayloadEncoderTest {

    @Test
    void 인코더_인스턴스_생성이_가능하다() {
        // when
        OutboxIdempotencyPayloadEncoder encoder = new OutboxIdempotencyPayloadEncoder();

        // then
        assertThat(encoder).isNotNull();
    }

    @Test
    void 입력_값을_길이_접두_포맷으로_인코딩한다() {
        // given
        OutboxIdempotencyPayloadEncoder encoder = new OutboxIdempotencyPayloadEncoder();

        // when
        String actual = encoder.encode(
                "SRC",
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                "T1",
                "C1",
                null
        );

        // then
        assertAll(
                () -> assertThat(actual).contains("source=3#SRC"),
                () -> assertThat(actual).contains("messageType=14#CHANNEL_BLOCKS"),
                () -> assertThat(actual).contains("teamId=2#T1"),
                () -> assertThat(actual).contains("channelId=2#C1"),
                () -> assertThat(actual).contains("userId=0#")
        );
    }
}
