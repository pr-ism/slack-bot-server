package com.slack.bot.application.oauth.exception;

public class SlackOauthEmptyResponseException extends RuntimeException {

    public SlackOauthEmptyResponseException(String message) {
        super(message);
    }
}
