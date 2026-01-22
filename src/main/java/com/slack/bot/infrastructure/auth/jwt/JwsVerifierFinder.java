package com.slack.bot.infrastructure.auth.jwt;

import com.nimbusds.jose.JWSVerifier;
import com.slack.bot.domain.auth.TokenType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwsVerifierFinder {

    private final JWSVerifier accessTokenJwsVerifier;
    private final JWSVerifier refreshTokenJwsVerifier;

    public JWSVerifier findByTokenType(TokenType tokenType) {
        if (tokenType.isAccessToken()) {
            return accessTokenJwsVerifier;
        }

        return refreshTokenJwsVerifier;
    }
}
