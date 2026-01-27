package com.slack.bot.infrastructure.member.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import com.slack.bot.domain.member.vo.GithubId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProjectMemberRepositoryAdapterTest {

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/member/project_member_team1_user1.sql")
    void 중복_멤버_저장_시_기존_멤버를_반환하고_Github_ID를_갱신한다() {
        // given
        ProjectMember duplicate = ProjectMember.builder()
                                               .teamId("T1")
                                               .slackUserId("U1")
                                               .displayName("새 사용자")
                                               .build();
        duplicate.connectGithubId(GithubId.create("git-2"));

        // when
        ProjectMember actualResolved = projectMemberRepository.save(duplicate);
        Optional<ProjectMember> actualPersisted = projectMemberRepository.findBySlackUser("T1", "U1");

        // then
        assertAll(
                () -> assertThat(actualResolved.getDisplayName()).isEqualTo("기존 사용자"),
                () -> assertThat(actualResolved.getGithubId().getValue()).isEqualTo("git-2"),
                () -> assertThat(actualPersisted).isPresent(),
                () -> assertThat(actualPersisted.get().getDisplayName()).isEqualTo("기존 사용자"),
                () -> assertThat(actualPersisted.get().getGithubId().getValue()).isEqualTo("git-2")
        );
    }
}
