package com.slack.bot.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.AccessLinker;
import com.slack.bot.application.command.ProjectMemberReader;
import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.application.command.handler.fixture.SlackCommandContextDtoFixture;
import com.slack.bot.global.config.properties.AppProperties;
import com.slack.bot.global.config.properties.CommandMessageProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MyPageCommandHandlerTest {

    @Autowired
    ProjectMemberReader projectMemberReader;

    @Autowired
    AccessLinker accessLinker;

    @Autowired
    AppProperties appProperties;

    @Autowired
    CommandMessageProperties commandMessageProperties;

    MyPageCommandHandler command;

    @BeforeEach
    void setUp() {
        command = MyPageCommandHandler.create(
                projectMemberReader,
                accessLinker,
                appProperties,
                commandMessageProperties
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/command/project_member_t1_u1_github.sql")
    void 깃허브_아이디가_연결된_사용자라면_전용_링크를_응답한다() {
        // given
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(
                "my-page",
                "U1",
                "T1",
                List.of("my-page"),
                "my-page"
        );

        // when
        String actual = command.handle(context);

        // then
        assertAll(
                () -> assertThat(actual).contains("내 전용 설정 링크"),
                () -> assertThat(actual).contains("http://localhost:8080/links/")
        );
    }

    @Test
    void 깃허브_아이디가_연결되지_않은_사용자라면_안내문구를_응답한다() {
        // given
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(
                "my-page",
                "U1",
                "T1",
                List.of("my-page"),
                "my-page"
        );

        // when
        String actual = command.handle(context);

        // then
        assertThat(actual).contains("아직 GitHub ID가 연결되지 않았습니다");
    }
}
