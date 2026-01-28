package com.slack.bot.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.application.command.handler.fixture.SlackCommandContextDtoFixture;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CommandHandlerRegistryTest {

    @Autowired
    CommandHandlerRegistry registry;

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @Test
    void 도움말_명령을_실행한다() {
        // given
        String key = "help";
        String userId = "U1";
        String teamId = "T1";
        List<String> arguments = List.of(key);
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(key, userId, teamId, arguments, key);

        // when
        String actual = registry.handle(context);

        // then
        assertThat(actual).contains("/prism connect");
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/command/workspace_t1.sql")
    void 깃허브_아이디_연결_명령을_실행한다() {
        // given
        String teamId = "T1";
        String slackUserId = "U1";
        String key = "connect";
        String githubId = "gildong";
        String text = "connect gildong";

        List<String> arguments = List.of(key, githubId);
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(text, slackUserId, teamId, arguments, key);

        // when
        String actual = registry.handle(context);

        // then
        Optional<ProjectMember> actualProjectMember = projectMemberRepository.findBySlackUser(teamId, slackUserId);

        assertAll(
                () -> assertThat(actual).contains("✅"),
                () -> assertThat(actual).contains(githubId),
                () -> assertThat(actualProjectMember).isPresent(),
                () -> assertThat(actualProjectMember.get().getGithubId().getValue()).isEqualTo(githubId)
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/command/project_member_t1_u1_github.sql")
    void 링크_조회_명령을_실행한다() {
        // given
        String teamId = "T1";
        String userId = "U1";
        String key = "my-page";
        List<String> arguments = List.of(key);
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(key, userId, teamId, arguments, key);

        // when
        String response = registry.handle(context);

        // then
        assertThat(response).contains("내 전용 설정 링크");
    }

    @Test
    void 알_수_없는_키는_unknown_커맨드를_실행한다() {
        // given
        String key = "nope";
        String userId = "U1";
        String teamId = "T1";
        List<String> arguments = List.of(key);
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(key, userId, teamId, arguments, key);

        // when
        String response = registry.handle(context);

        // then
        assertThat(response).contains("알 수 없는 명령어");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 명령어가_비어_있다면_unknown_커맨드를_실행한다(String key) {
        // given
        String text = "";
        String userId = "U1";
        String teamId = "T1";
        List<String> arguments = List.of();
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(
                text,
                userId,
                teamId,
                arguments,
                key
        );

        // when
        String response = registry.handle(context);

        // then
        assertThat(response).contains("알 수 없는 명령어");
    }
}
