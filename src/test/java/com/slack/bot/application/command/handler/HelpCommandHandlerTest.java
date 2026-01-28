package com.slack.bot.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.application.command.handler.fixture.SlackCommandContextDtoFixture;
import com.slack.bot.global.config.properties.CommandMessageProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HelpCommandHandlerTest {

    @Autowired
    CommandMessageProperties commandMessageProperties;

    @Test
    void 슬랙_봇_명령어_도움말을_조회한다() {
        // given
        HelpCommandHandler command = HelpCommandHandler.create(commandMessageProperties);
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(
                "help",
                "U1",
                "T1",
                List.of("help"),
                "help"
        );

        // when
        String actual = command.handle(context);

        // then
        assertThat(actual).contains("/prism connect")
                          .contains("/prism channels")
                          .contains("/prism my-page")
                          .contains("/prism help");
    }
}
