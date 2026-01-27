package com.slack.bot.infrastructure.link.persistence.exception;

public class AccessLinkDuplicateKeyException extends RuntimeException {

    public AccessLinkDuplicateKeyException() {
        super("AccessLink 중복 저장 후 조회에 실패했습니다.");
    }
}
