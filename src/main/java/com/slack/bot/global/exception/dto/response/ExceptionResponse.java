package com.slack.bot.global.exception.dto.response;

public record ExceptionResponse(String errorCode, String message) {

    public static ExceptionResponse from(ErrorCode errorCode) {
        return new ExceptionResponse(errorCode.getErrorCode(), errorCode.getMessage());
    }
}
