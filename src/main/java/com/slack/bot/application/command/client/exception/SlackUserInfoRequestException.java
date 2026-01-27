package com.slack.bot.application.command.client.exception;

public class SlackUserInfoRequestException extends RuntimeException {

    public SlackUserInfoRequestException(int statusCode) {
        super("Slack users.info API 요청에 실패했습니다. status=" + statusCode);
    }
}
