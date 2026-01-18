package com.slack.bot.global.exception;

import com.slack.bot.application.oauth.exception.SlackOauthEmptyResponseException;
import com.slack.bot.application.oauth.exception.SlackOauthErrorResponseException;
import com.slack.bot.application.oauth.exception.SlackOauthInvalidStateException;
import com.slack.bot.global.exception.dto.response.OauthErrorCode;
import com.slack.bot.global.exception.dto.response.DefaultErrorCode;
import com.slack.bot.global.exception.dto.response.ErrorCode;
import com.slack.bot.global.exception.dto.response.ExceptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception ex) {
        log.error("Exception : ", ex);

        return createResponseEntity(DefaultErrorCode.UNKNOWN_SERVER_EXCEPTION);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.info("IllegalArgumentException : {}", ex.getMessage());

        return createResponseEntity(DefaultErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(SlackOauthEmptyResponseException.class)
    public ResponseEntity<ExceptionResponse> handleSlackOauthEmptyResponseException(SlackOauthEmptyResponseException ex) {
        log.info("SlackOauthEmptyResponseException : {}", ex.getMessage());

        return createResponseEntity(OauthErrorCode.SLACK_OAUTH_EMPTY_RESPONSE);
    }

    @ExceptionHandler(SlackOauthErrorResponseException.class)
    public ResponseEntity<ExceptionResponse> handleSlackOauthErrorResponseException(SlackOauthErrorResponseException ex) {
        log.info("SlackOauthErrorResponseException : {}", ex.getMessage());

        return createResponseEntity(OauthErrorCode.SLACK_OAUTH_ERROR_RESPONSE);
    }

    @ExceptionHandler(SlackOauthInvalidStateException.class)
    public ResponseEntity<ExceptionResponse> handleSlackOauthInvalidStateException(SlackOauthInvalidStateException ex) {
        log.info("SlackOauthInvalidStateException : {}", ex.getMessage());

        return createResponseEntity(OauthErrorCode.SLACK_OAUTH_INVALID_STATE);
    }

    private ResponseEntity<ExceptionResponse> createResponseEntity(ErrorCode errorCode) {
        ExceptionResponse response = ExceptionResponse.from(errorCode);

        return ResponseEntity.status(errorCode.getHttpStatus())
                             .body(response);
    }
}
