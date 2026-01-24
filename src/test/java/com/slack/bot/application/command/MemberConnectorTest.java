package com.slack.bot.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberConnectorTest {

    @Autowired
    MemberConnector memberConnector;

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/member/workspace_t1.sql",
            "classpath:sql/fixtures/member/project_member_t1_u2.sql"
    })
    void 이미_연동된_멤버는_새로운_Github_ID로_연동한다() {
        // when
        String actualConnectedName = memberConnector.connectUser("T1", "U2", "gildong");

        // then
        Optional<ProjectMember> actualUpdatedMember = projectMemberRepository.findBySlackUser("T1", "U2");

        assertAll(
                () -> assertThat(actualConnectedName).isEqualTo("홍길동"),
                () -> assertThat(actualUpdatedMember).isPresent(),
                () -> assertThat(actualUpdatedMember.get().getGithubId().getValue()).isEqualTo("gildong")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/member/workspace_t1.sql")
    void 멤버가_없으면_새_멤버를_생성한다() {
        // when
        String actualConnectedName = memberConnector.connectUser("T1", "U3", "gildong");

        // then
        Optional<ProjectMember> actualSavedMember = projectMemberRepository.findBySlackUser("T1", "U3");

        assertAll(
                () -> assertThat(actualConnectedName).isEqualTo("신규 사용자"),
                () -> assertThat(actualSavedMember).isPresent(),
                () -> assertThat(actualSavedMember.get().getDisplayName()).isEqualTo("신규 사용자"),
                () -> assertThat(actualSavedMember.get().getGithubId().getValue()).isEqualTo("gildong")
        );
    }

    @Test
    void 해당_팀에_대한_워크스페이스가_등록되어_있지_않다면_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> memberConnector.connectUser("T1", "U9", "gildong"))
                .isInstanceOf(WorkspaceNotFoundException.class)
                .hasMessage("워크스페이스 정보를 찾을 수 없습니다.");
    }
}
