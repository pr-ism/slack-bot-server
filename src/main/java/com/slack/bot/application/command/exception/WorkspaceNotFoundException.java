package com.slack.bot.application.command.exception;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException() {
        super("워크스페이스 정보를 찾을 수 없습니다.");
    }
}
