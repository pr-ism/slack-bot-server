package com.slack.bot.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.domain.member.vo.GithubId;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProjectMemberTest {

    @Test
    void 프로젝트에_참여하는_멤버를_초기화한다() {
        // when & then
        ProjectMember projectMember = assertDoesNotThrow(
                () -> ProjectMember.builder()
                                   .teamId("T1")
                                   .slackUserId("U1")
                                   .displayName("홍길동")
                                   .build()
        );

        assertAll(
                () -> assertThat(projectMember.getTeamId()).isEqualTo("T1"),
                () -> assertThat(projectMember.getSlackUserId()).isEqualTo("U1"),
                () -> assertThat(projectMember.getDisplayName()).isEqualTo("홍길동"),
                () -> assertThat(projectMember.getGithubId()).isSameAs(GithubId.EMPTY)
        );
    }

    @Test
    void 연결된_깃허브_ID를_갱신한다() {
        // given
        ProjectMember projectMember = ProjectMember.builder()
                                                   .teamId("T1")
                                                   .slackUserId("U1")
                                                   .displayName("홍길동")
                                                   .build();
        GithubId newGithubId = GithubId.create("new-id");

        // when
        projectMember.connectGithubId(newGithubId);

        // then
        assertThat(projectMember.getGithubId().getValue()).isEqualTo("new-id");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 팀_ID가_비어_있으면_초기화할_수_없다(String value) {
        // when & then
        assertThatThrownBy(
                () -> ProjectMember.builder()
                                   .teamId(value)
                                   .slackUserId("U1")
                                   .displayName("홍길동")
                                   .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("팀 ID는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 슬랙_사용자_ID가_비어_있으면_초기화할_수_없다(String value) {
        // when & then
        assertThatThrownBy(
                () -> ProjectMember.builder()
                                   .teamId("T1")
                                   .slackUserId(value)
                                   .displayName("홍길동")
                                   .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("슬랙 사용자 ID는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 표시_이름이_비어_있으면_초기화할_수_없다(String value) {
        // when & then
        assertThatThrownBy(
                () -> ProjectMember.builder()
                                   .teamId("T1")
                                   .slackUserId("U1")
                                   .displayName(value)
                                   .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("표시 이름은 비어 있을 수 없습니다.");
    }
}

