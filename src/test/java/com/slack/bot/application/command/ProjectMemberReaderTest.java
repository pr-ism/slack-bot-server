package com.slack.bot.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.member.ProjectMember;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProjectMemberReaderTest {

    @Autowired
    ProjectMemberReader projectMemberReader;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/member/project_member_team1_user1.sql")
    void 프로젝트_멤버를_조회한다() {
        // given
        // when
        Optional<ProjectMember> actualMember = projectMemberReader.read("T1", "U1");

        // then
        assertAll(
                () -> assertThat(actualMember).isPresent(),
                () -> assertThat(actualMember.get().getSlackUserId()).isEqualTo("U1"),
                () -> assertThat(actualMember.get().getTeamId()).isEqualTo("T1")
        );
    }
}
