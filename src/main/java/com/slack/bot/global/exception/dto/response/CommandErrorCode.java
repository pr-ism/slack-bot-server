package com.slack.bot.global.exception.dto.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommandErrorCode implements ErrorCode {

    WORK_SPACE_NOT_FOUND("C00", "워크스페이스 없음", HttpStatus.BAD_REQUEST),
    DUPLICATE_LINK_KEY("C01", "고유한 링크 키 생성 실패", HttpStatus.INTERNAL_SERVER_ERROR),
    SLACK_USER_INFO_API_FAILED("C02", "슬랙 API 실패", HttpStatus.INTERNAL_SERVER_ERROR),
    LINK_SEQUENCE_NOT_EXISTS("C03", "링크 시퀀스 조회 실패", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;

    CommandErrorCode(String errorCode, String message, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
