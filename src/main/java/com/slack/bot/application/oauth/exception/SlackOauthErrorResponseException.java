package com.slack.bot.application.oauth.exception;

public class SlackOauthErrorResponseException extends RuntimeException {

    public SlackOauthErrorResponseException(String message) {
        super(message);
    }

    public SlackOauthErrorResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
