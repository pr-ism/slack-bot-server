package com.slack.bot.global.exception.dto.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum OauthErrorCode implements ErrorCode {

    SLACK_OAUTH_EMPTY_RESPONSE("O00", "슬랙 OAuth 실패", HttpStatus.UNAUTHORIZED),
    SLACK_OAUTH_ERROR_RESPONSE("O01", "슬랙 OAuth 실패", HttpStatus.UNAUTHORIZED),
    SLACK_OAUTH_EXPIRED_STATE("O02", "만료된 state", HttpStatus.BAD_REQUEST),
    SLACK_OAUTH_NOT_FOUND_STATE("O03", "존재하지 않는 state", HttpStatus.BAD_REQUEST);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;

    OauthErrorCode(String errorCode, String message, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
