package com.slack.bot.global.exception.dto.response;

import lombok.Getter;

@Getter
public enum DefaultErrorCode implements ErrorCode {

    UNKNOWN_SERVER_EXCEPTION("서버 에러");

    private final String message;

    DefaultErrorCode(String message) {
        this.message = message;
    }
    
    @Override
    public String getErrorCode() {
        return this.name();
    }
}
