package com.slack.bot.domain.auth;

public interface TokenDecoder {

    PrivateClaims decode(TokenType tokenType, String token);
}
