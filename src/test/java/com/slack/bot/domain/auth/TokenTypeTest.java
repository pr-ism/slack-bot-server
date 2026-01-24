package com.slack.bot.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TokenTypeTest {

    @Test
    void ACCESS_토큰인지_확인한다() {
        // given
        TokenType tokenType = TokenType.ACCESS;

        // when
        boolean actual = tokenType.isAccessToken();

        // then
        assertThat(actual).isTrue();
    }
}
