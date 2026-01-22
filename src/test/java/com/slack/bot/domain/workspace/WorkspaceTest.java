package com.slack.bot.domain.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WorkspaceTest {

    @Test
    void 워크스페이스를_초기화한다() {
        // when & then
        Workspace actual = assertDoesNotThrow(() -> Workspace.builder()
                                                             .teamId("T123")
                                                             .accessToken("accessToken")
                                                             .botUserId("test-bot-user")
                                                             .userId(1L)
                                                             .build());

        assertAll(
                () -> assertThat(actual.getTeamId()).isEqualTo("T123"),
                () -> assertThat(actual.getAccessToken()).isEqualTo("accessToken"),
                () -> assertThat(actual.getBotUserId()).isEqualTo("test-bot-user"),
                () -> assertThat(actual.getUserId()).isEqualTo(1L)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_봇_access_token이_비어_있다면_워크스페이스를_초기화_할_수_없다(String accessToken) {
        // when & then
        assertThatThrownBy(() -> Workspace.builder()
                                          .teamId("T123")
                                          .accessToken(accessToken)
                                          .botUserId("test-bot-user")
                                          .userId(1L)
                                          .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("슬랙 봇의 access token은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_워크스페이스_ID가_비어_있다면_워크스페이스를_초기화_할_수_없다(String teamId) {
        // when & then
        assertThatThrownBy(() -> Workspace.builder()
                                          .teamId(teamId)
                                          .accessToken("accessToken")
                                          .botUserId("test-bot-user")
                                          .userId(1L)
                                          .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("슬랙 봇의 team ID는 비어 있을 수 없습니다.");
    }

    @Test
    void 워크스페이스를_재연결한다() {
        // given
        Workspace workspace = Workspace.builder()
                                       .teamId("T123")
                                       .accessToken("old-token")
                                       .botUserId("test-bot-user")
                                       .userId(1L)
                                       .build();

        // when
        workspace.reconnect("new-token");

        // then
        assertAll(
                () -> assertThat(workspace.getTeamId()).isEqualTo("T123"),
                () -> assertThat(workspace.getAccessToken()).isEqualTo("new-token"),
                () -> assertThat(workspace.getBotUserId()).isEqualTo("test-bot-user")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_봇_access_token이_비어_있다면_재연결할_수_없다(String accessToken) {
        // given
        Workspace workspace = Workspace.builder()
                                       .teamId("T123")
                                       .accessToken("old-token")
                                       .botUserId("test-bot-user")
                                       .userId(1L)
                                       .build();

        // when & then
        assertThatThrownBy(() -> workspace.reconnect(accessToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("슬랙 봇의 access token은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_봇_user_id가_비어있다면_워크스페이스를_초기화할_수_없다(String botUserId) {
        assertThatThrownBy(() -> Workspace.builder()
                                          .teamId("T123")
                                          .accessToken("accessToken")
                                          .botUserId(botUserId)
                                          .userId(1L)
                                          .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("슬랙 봇의 user ID는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullSource
    void 워크스페이스_생성_회원_ID가_null이면_워크스페이스를_초기화할_수_없다(Long userId) {
        assertThatThrownBy(() -> Workspace.builder()
                                          .teamId("T123")
                                          .accessToken("accessToken")
                                          .botUserId("bot-user")
                                          .userId(userId)
                                          .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("워크스페이스 생성 회원 ID는 비어 있을 수 없습니다.");
    }
}
