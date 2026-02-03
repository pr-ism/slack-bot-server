package com.slack.bot.application.interactivity.reservation.exception;

public class DefaultProjectNotFoundException extends RuntimeException {

    public DefaultProjectNotFoundException(String teamId) {
        super("teamId로 기본 프로젝트를 찾을 수 없습니다: " + teamId);
    }
}
