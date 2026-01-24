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
class AccessLinkerTest {

    @Autowired
    AccessLinker accessLinker;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/link/project_member_t1_u1.sql")
    void 프로젝트_멤버가_개인_설정_링크키를_받는다() {
        // given
        Long projectMemberId = 1L;

        // when
        String linkKey = accessLinker.provideLinkUrl(projectMemberId);

        // then
        assertThat(linkKey).isNotBlank();
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/link/project_member_t1_u1.sql")
    void 링크키로_멤버를_조회한다() {
        // given
        Long projectMemberId = 1L;
        String linkKey = accessLinker.provideLinkUrl(projectMemberId);

        // when
        Optional<ProjectMember> resolvedMember = accessLinker.resolve(linkKey);

        // then
        assertAll(
                () -> assertThat(resolvedMember).isPresent(),
                () -> assertThat(resolvedMember.get().getId()).isEqualTo(projectMemberId)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/link/project_member_t1_u1.sql",
            "classpath:sql/fixtures/link/access_link_t1_u1.sql"
    })
    void 링크키로_연결된_멤버를_단건_조회한다() {
        // given
        String linkKey = "linkKey01";

        // when
        Optional<ProjectMember> resolvedMember = accessLinker.resolve(linkKey);

        // then
        assertAll(
                () -> assertThat(resolvedMember).isPresent(),
                () -> assertThat(resolvedMember.get().getId()).isEqualTo(1L)
        );
    }
}

