package com.slack.bot.application.event.handler.exception;

public class BotUserIdMissingException extends RuntimeException {

    public BotUserIdMissingException(String teamId) {
        super("봇 사용자 ID가 등록되지 않은 워크스페이스입니다: " + teamId);
    }
}
