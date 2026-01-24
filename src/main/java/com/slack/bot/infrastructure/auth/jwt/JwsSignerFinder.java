package com.slack.bot.infrastructure.auth.jwt;

import com.nimbusds.jose.JWSSigner;
import com.slack.bot.domain.auth.TokenType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwsSignerFinder {

    private final JWSSigner accessTokenSigner;
    private final JWSSigner refreshTokenSigner;

    public JWSSigner findByTokenType(TokenType tokenType) {
        if (tokenType.isAccessToken()) {
            return accessTokenSigner;
        }

        return refreshTokenSigner;
    }
}
