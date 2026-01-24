package com.slack.bot.application.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.oauth.exception.EmptyAccessTokenException;
import com.slack.bot.domain.auth.PrivateClaims;
import com.slack.bot.domain.auth.TokenDecoder;
import com.slack.bot.domain.auth.TokenType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
        Long actual = tokenParsingService.extractUserId("access-token");

        // then
        assertThat(actual).isEqualTo(42L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void access_token이_비어있다면_디코딩할_수_없다(String accessToken) {
        // when & then
        assertThatThrownBy(() -> tokenParsingService.extractUserId(accessToken))
                .isInstanceOf(EmptyAccessTokenException.class)
                .hasMessage("access token이 비어 있습니다.");
    }
}
