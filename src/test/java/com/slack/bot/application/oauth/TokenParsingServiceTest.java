package com.slack.bot.application.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.auth.PrivateClaims;
import com.slack.bot.domain.auth.TokenDecoder;
import com.slack.bot.domain.auth.TokenType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TokenParsingServiceTest {

    @Autowired
    private TokenParsingService tokenParsingService;

    @Autowired
    private TokenDecoder tokenDecoder;

    @Test
    void access_token을_디코딩한다() {
        // given
        PrivateClaims claims = new PrivateClaims(42L, LocalDateTime.now());
        given(tokenDecoder.decode(TokenType.ACCESS, "access-token")).willReturn(claims);

        // when
        Long actual = tokenParsingService.encode("access-token");

        // then
        assertThat(actual).isEqualTo(42L);
    }
}
