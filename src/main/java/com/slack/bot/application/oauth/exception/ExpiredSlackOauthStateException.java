package com.slack.bot.application.oauth.exception;

public class ExpiredSlackOauthStateException extends RuntimeException {

    public ExpiredSlackOauthStateException() {
        super("만료된 state 입니다.");
    }
}
