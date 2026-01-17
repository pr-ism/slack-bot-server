package com.slack.bot.application.oauth.exception;

public class SlackOauthErrorResponseException extends RuntimeException {

    public SlackOauthErrorResponseException(String message) {
        super(message);
    }
}
