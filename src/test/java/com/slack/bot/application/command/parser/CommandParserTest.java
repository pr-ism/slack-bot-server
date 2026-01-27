package com.slack.bot.application.command.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.command.dto.SlackParsedCommand;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CommandParserTest {

    @ParameterizedTest
    @NullAndEmptySource
    void 비어_있는_문자열은_빈_명령으로_파싱된다(String text) {
        // given
        CommandParser parser = new CommandParser();

        // when
        SlackParsedCommand actual = parser.parse(text);

        // then
        assertAll(
                () -> assertThat(actual.commandName()).isEmpty(),
                () -> assertThat(actual.arguments()).isEmpty()
        );
    }

    @Test
    void 공백_텍스트는_빈_인자로_처리된다() {
        // given
        CommandParser parser = new CommandParser();

        // when
        SlackParsedCommand actual = parser.parse("   ");

        // then
        assertAll(
                () -> assertThat(actual.commandName()).isEmpty(),
                () -> assertThat(actual.arguments()).isEqualTo(List.of())
        );
    }

    @Test
    void 텍스트는_토큰으로_분리된다() {
        // given
        CommandParser parser = new CommandParser();

        // when
        SlackParsedCommand actual = parser.parse("connect gildong");

        // then
        assertAll(
                () -> assertThat(actual.commandName()).isEqualTo("connect"),
                () -> assertThat(actual.arguments()).containsExactly("connect", "gildong")
        );
    }
}
