package com.slack.bot.infrastructure.link.persistence.exception;

public class AccessLinkSequenceStateException extends IllegalStateException {

    public AccessLinkSequenceStateException() {
        super("AccessLink 시퀀스를 찾을 수 없습니다.");
    }
}
