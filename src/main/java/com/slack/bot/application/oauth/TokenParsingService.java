package com.slack.bot.application.oauth;

import com.slack.bot.application.oauth.exception.EmptyAccessTokenException;
import com.slack.bot.domain.auth.PrivateClaims;
import com.slack.bot.domain.auth.TokenDecoder;
import com.slack.bot.domain.auth.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenParsingService {

    private final TokenDecoder tokenDecoder;

    public Long encode(String accessToken) {
        validateAccessToken(accessToken);

        PrivateClaims privateClaims = tokenDecoder.decode(TokenType.ACCESS, accessToken);

        return privateClaims.userId();
    }

    private void validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new EmptyAccessTokenException();
        }
    }
}
