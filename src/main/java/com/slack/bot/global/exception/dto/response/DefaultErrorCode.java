package com.slack.bot.global.exception.dto.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum DefaultErrorCode implements ErrorCode {

    UNKNOWN_SERVER_EXCEPTION("D00", "서버 에러", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT("D01", "유효하지 않은 입력", HttpStatus.BAD_REQUEST);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;

    DefaultErrorCode(String errorCode, String message, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
