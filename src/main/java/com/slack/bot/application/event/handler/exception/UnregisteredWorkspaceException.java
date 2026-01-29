package com.slack.bot.application.event.handler.exception;

public class UnregisteredWorkspaceException extends RuntimeException {

    public UnregisteredWorkspaceException() {
        super("설치되지 않은 워크스페이스입니다.");
    }
}
