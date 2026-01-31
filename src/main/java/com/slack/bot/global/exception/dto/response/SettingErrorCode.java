package com.slack.bot.global.exception.dto.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SettingErrorCode implements ErrorCode {

    NOTIFICATION_SETTINGS_CONFLICT("N00", "개인 알림 설정 충돌", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;

    SettingErrorCode(String errorCode, String message, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
