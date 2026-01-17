package com.slack.bot.global.exception.dto.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum OauthErrorCode implements ErrorCode {

    SLACK_OAUTH_EMPTY_RESPONSE("A00", "슬랙 OAuth 실패", HttpStatus.UNAUTHORIZED),
    SLACK_OAUTH_ERROR_RESPONSE("A01", "슬랙 OAuth 실패", HttpStatus.UNAUTHORIZED);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;

    OauthErrorCode(String errorCode, String message, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
