package com.slack.bot.application.oauth.exception;

public class SlackOauthInvalidStateException extends RuntimeException {

    public SlackOauthInvalidStateException() {
        super("유효하지 않은 OAuth state 입니다.");
    }
}
