package com.slack.bot.application.oauth.exception;

public class SlackOauthInvalidStateException extends RuntimeException {

    public SlackOauthInvalidStateException(String message) {
        super(message);
    }
}
