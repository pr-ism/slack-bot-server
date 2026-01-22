package com.slack.bot.application.oauth.exception;

public class SlackOauthStateNotFoundException extends RuntimeException {

    public SlackOauthStateNotFoundException() {
        super("존재하지 않는 state 입니다.");
    }
}
