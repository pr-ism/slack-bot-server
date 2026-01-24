package com.slack.bot.application.oauth.exception;

public class EmptyAccessTokenException extends RuntimeException {

    public EmptyAccessTokenException() {
        super("access token이 비어 있습니다.");
    }
}
