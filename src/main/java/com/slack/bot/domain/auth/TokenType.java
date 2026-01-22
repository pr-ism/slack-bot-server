package com.slack.bot.domain.auth;

public enum TokenType {
    ACCESS;

    public boolean isAccessToken() {
        return this == TokenType.ACCESS;
    }
}
