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
class UnknownCommandHandlerTest {

    @Autowired
    CommandMessageProperties commandMessageProperties;

    @Test
    void 알_수_없는_명령을_입력한_경우_안내_문구를_전달한다() {
        // given
        UnknownCommandHandler command = UnknownCommandHandler.create(commandMessageProperties);
        SlackCommandContextDto context = SlackCommandContextDtoFixture.create(
                "what",
                "U1",
                "T1",
                List.of("what"),
                "what"
        );

        // when
        String actual = command.handle(context);

        // then
        assertThat(actual).contains("알 수 없는 명령어");
    }
}
