package com.slack.bot.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelTest {

    @Test
    void 채널을_초기화한다() {
        // when & then
        Channel channel = assertDoesNotThrow(
                () -> Channel.builder()
                             .apiKey("api-key")
                             .teamId("T1")
                             .channelId("C1")
                             .build()
        );

        assertAll(
                () -> assertThat(channel.getApiKey()).isEqualTo("api-key"),
                () -> assertThat(channel.getTeamId()).isEqualTo("T1"),
                () -> assertThat(channel.getChannelId()).isEqualTo("C1")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void apiKey가_비어_있으면_초기화할_수_없다(String apiKey) {
        // when & then
        assertThatThrownBy(
                () -> Channel.builder()
                             .apiKey(apiKey)
                             .teamId("T1")
                             .channelId("C1")
                             .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("채널 API 키는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void teamId가_비어_있으면_초기화할_수_없다(String teamId) {
        // when & then
        assertThatThrownBy(
                () -> Channel.builder()
                             .apiKey("api-key")
                             .teamId(teamId)
                             .channelId("C1")
                             .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("채널의 team ID는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void channelId가_비어_있으면_초기화할_수_없다(String channelId) {
        // when & then
        assertThatThrownBy(
                () -> Channel.builder()
                             .apiKey("api-key")
                             .teamId("T1")
                             .channelId(channelId)
                             .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("채널 ID는 비어 있을 수 없습니다.");
    }

    @Test
    void apiKey를_갱신한다() {
        // given
        Channel channel = Channel.builder()
                                 .apiKey("old-key")
                                 .teamId("T1")
                                 .channelId("C1")
                                 .build();

        // when
        channel.regenerateApiKey("new-key");

        // then
        assertThat(channel.getApiKey()).isEqualTo("new-key");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void apiKey가_비어_있으면_갱신할_수_없다(String apiKey) {
        // given
        Channel channel = Channel.builder()
                                 .apiKey("old-key")
                                 .teamId("T1")
                                 .channelId("C1")
                                 .build();

        // when & then
        assertThatThrownBy(() -> channel.regenerateApiKey(apiKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("채널 API 키는 비어 있을 수 없습니다.");
    }
}
