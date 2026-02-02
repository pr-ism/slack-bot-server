package com.slack.bot.global.exception.dto.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ReviewErrorCode implements ErrorCode {

    REVIEW_CHANNEL_NOT_FOUND("R00", "리뷰 채널 정보를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    REVIEW_PROJECT_NOT_FOUND("R01", "프로젝트를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    REVIEW_ACTION_META_BUILD_FAILED("R02", "리뷰 액션 메타데이터 생성 실패", HttpStatus.INTERNAL_SERVER_ERROR),
    REVIEW_SLACK_API_FAILED("R03", "리뷰 알림 Slack API 요청 실패", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus httpStatus;

    ReviewErrorCode(String errorCode, String message, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
