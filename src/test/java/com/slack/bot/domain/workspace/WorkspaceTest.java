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

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WorkspaceTest {

    @Test
    void 워크스페이스를_초기화한다() {
        // when & then
        Workspace actual = assertDoesNotThrow(() -> Workspace.create("T123", "accessToken", "installer"));

        assertAll(
                () -> assertThat(actual.getTeamId()).isEqualTo("T123"),
                () -> assertThat(actual.getAccessToken()).isEqualTo("accessToken"),
                () -> assertThat(actual.getInstalledBy()).isEqualTo("installer")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_봇_access_token이_비어_있다면_워크스페이스를_초기화_할_수_없다(String accessToken) {
        // when & then
        assertThatThrownBy(() -> Workspace.create("T123", accessToken, "installer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("슬랙 봇의 access token은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_봇_설치_회원이_비어_있다면_워크스페이스를_초기화_할_수_없다(String installer) {
        // when & then
        assertThatThrownBy(() -> Workspace.create("T123", "accessToken", installer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("슬랙 봇을 설치한 회원은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_워크스페이스_ID가_비어_있다면_워크스페이스를_초기화_할_수_없다(String teamId) {
        // when & then
        assertThatThrownBy(() -> Workspace.create(teamId, "accessToken", "installer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("슬랙 봇의 team ID는 비어 있을 수 없습니다.");
    }
}
