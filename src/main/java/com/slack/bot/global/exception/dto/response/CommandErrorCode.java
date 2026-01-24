package com.slack.bot.global.exception.dto.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommandErrorCode implements ErrorCode {

    WORK_SPACE_NOT_FOUND("C00", "워크스페이스 없음", HttpStatus.BAD_REQUEST);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;

    CommandErrorCode(String errorCode, String message, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
