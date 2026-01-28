package com.slack.bot.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.MemberConnector;
import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.application.command.handler.fixture.SlackCommandContextDtoFixture;
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
class ConnectCommandHandlerTest {

    @Autowired
    MemberConnector memberService;

    @Autowired
    CommandMessageProperties commandMessageProperties;

    ConnectCommandHandler command;

    @BeforeEach
    void setUp() {
        command = ConnectCommandHandler.create(memberService, commandMessageProperties);
    }

    @Test
    void 깃허브_아이디_인자가_없으면_안내문구를_보낸다() {
        // given
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(List.of("connect"));

        // when
        String actual = command.handle(context);

        // then
        assertThat(actual).contains("GitHub ID");
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/command/workspace_t1.sql")
    void 깃허브_아이디가_정상_연결되면_성공_메시지를_보낸다() {
        // given
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(List.of("connect", "gildong"));

        // when
        String actual = command.handle(context);

        // then
        assertAll(
                () -> assertThat(actual).contains("✅"),
                () -> assertThat(actual).contains("신규 사용자"),
                () -> assertThat(actual).contains("gildong")
        );
    }

    @Test
    void 깃허브_아이디_연결에_실패하면_오류_메시지를_보낸다() {
        // given
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(List.of("connect", "gildong"));

        // when
        String actual = command.handle(context);

        // then
        assertThat(actual).contains("❌");
    }
}
