package com.slack.bot.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.dto.request.SlackCommandRequest;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
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
class CommandServiceTest {

    @Autowired
    CommandService commandService;

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @ParameterizedTest
    @NullAndEmptySource
    void 텍스트가_없으면_알_수_없는_명령으로_처리한다(String text) {
        // given
        SlackCommandRequest request = new SlackCommandRequest(text, "U1", "T1");

        // when
        String actual = commandService.handle(request);

        // then
        assertThat(actual).contains("알 수 없는 명령어");
    }

    @Test
    void help_명령은_도움말을_응답한다() {
        // given
        SlackCommandRequest request = new SlackCommandRequest("help", "U1", "T1");

        // when
        String actual = commandService.handle(request);

        // then
        assertThat(actual).contains("/prism connect")
                          .contains("/prism channels")
                          .contains("/prism my-page")
                          .contains("/prism help");
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/command/project_member_t1_u1_github.sql")
    void my_page_명령은_링크_응답을_돌려준다() {
        // given
        String teamId = "T1";
        String userId = "U1";
        String text = "my-page";
        SlackCommandRequest request = new SlackCommandRequest(text, userId, teamId);

        // when
        String actual = commandService.handle(request);

        // then
        assertThat(actual).contains("내 전용 설정 링크");
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/command/workspace_t1.sql")
    void connect_명령은_계정을_연결한다() {
        // given
        String teamId = "T1";
        String slackUserId = "U1";
        String text = "connect gildong";
        SlackCommandRequest request = new SlackCommandRequest(text, slackUserId, teamId);

        // when
        String actual = commandService.handle(request);

        // then
        Optional<ProjectMember> actualSavedMember = projectMemberRepository.findBySlackUser(teamId, slackUserId);

        assertAll(
                () -> assertThat(actual).contains("✅"),
                () -> assertThat(actualSavedMember).isPresent(),
                () -> assertThat(actualSavedMember.get().getGithubId().getValue()).isEqualTo("gildong")
        );
    }
}
