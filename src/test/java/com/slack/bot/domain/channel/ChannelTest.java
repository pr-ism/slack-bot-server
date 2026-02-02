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
                             .teamId("T1")
                             .slackChannelId("C1")
                             .channelName("N1")
                             .build()
        );

        assertAll(
                () -> assertThat(channel.getTeamId()).isEqualTo("T1"),
                () -> assertThat(channel.getSlackChannelId()).isEqualTo("C1"),
                () -> assertThat(channel.getChannelName()).isEqualTo("N1")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 채널_이름이_비어_있으면_초기화할_수_없다(String channelName) {
        // when & then
        assertThatThrownBy(
                () -> Channel.builder()
                             .teamId("T1")
                             .slackChannelId("C1")
                             .channelName(channelName)
                             .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("채널 이름은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void teamId가_비어_있으면_초기화할_수_없다(String teamId) {
        // when & then
        assertThatThrownBy(
                () -> Channel.builder()
                             .teamId(teamId)
                             .slackChannelId("C1")
                             .channelName("N1")
                             .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("채널의 team ID는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void slackChannelId가_비어_있으면_초기화할_수_없다(String slackChannelId) {
        // when & then
        assertThatThrownBy(
                () -> Channel.builder()
                             .teamId("T1")
                             .slackChannelId(slackChannelId)
                             .channelName("N1")
                             .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("슬랙 채널 ID는 비어 있을 수 없습니다.");
    }

    @Test
    void 채널_이름을_갱신한다() {
        // given
        Channel channel = Channel.builder()
                                 .teamId("T1")
                                 .slackChannelId("C1")
                                 .channelName("old-name")
                                 .build();

        // when
        channel.updateChannel("C1", "new-name");

        // then
        assertThat(channel.getChannelName()).isEqualTo("new-name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 채널_이름이_비어_있으면_갱신할_수_없다(String channelName) {
        // given
        Channel channel = Channel.builder()
                                 .teamId("T1")
                                 .slackChannelId("C1")
                                 .channelName("N1")
                                 .build();

        // when & then
        assertThatThrownBy(() -> channel.updateChannel("C1", channelName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("채널 이름은 비어 있을 수 없습니다.");
    }
}
