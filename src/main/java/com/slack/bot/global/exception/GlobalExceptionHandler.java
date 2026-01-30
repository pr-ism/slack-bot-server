package com.slack.bot.global.exception;

import com.slack.bot.application.command.client.exception.SlackUserInfoRequestException;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.application.oauth.exception.EmptyAccessTokenException;
import com.slack.bot.application.oauth.exception.ExpiredSlackOauthStateException;
import com.slack.bot.application.oauth.exception.SlackOauthEmptyResponseException;
import com.slack.bot.application.oauth.exception.SlackOauthErrorResponseException;
import com.slack.bot.application.oauth.exception.SlackOauthStateNotFoundException;
import com.slack.bot.global.exception.dto.response.AuthErrorCode;
import com.slack.bot.global.exception.dto.response.CommandErrorCode;
import com.slack.bot.global.exception.dto.response.OauthErrorCode;
import com.slack.bot.global.exception.dto.response.DefaultErrorCode;
import com.slack.bot.global.exception.dto.response.ErrorCode;
import com.slack.bot.global.exception.dto.response.ExceptionResponse;
import com.slack.bot.global.exception.dto.response.SettingErrorCode;
import com.slack.bot.infrastructure.auth.jwt.exception.InvalidTokenException;
import com.slack.bot.infrastructure.link.persistence.exception.AccessLinkDuplicateKeyException;
import com.slack.bot.infrastructure.link.persistence.exception.AccessLinkSequenceStateException;
import com.slack.bot.infrastructure.setting.exception.NotificationSettingsCreationConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception ex) {
        log.error("Exception : ", ex);

        return createResponseEntity(DefaultErrorCode.UNKNOWN_SERVER_EXCEPTION);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.info("IllegalArgumentException : {}", ex.getMessage());

        return createResponseEntity(DefaultErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(SlackOauthEmptyResponseException.class)
    public ResponseEntity<Object> handleSlackOauthEmptyResponseException(SlackOauthEmptyResponseException ex) {
        log.info("SlackOauthEmptyResponseException : {}", ex.getMessage());

        return createResponseEntity(OauthErrorCode.SLACK_OAUTH_EMPTY_RESPONSE);
    }

    @ExceptionHandler(SlackOauthErrorResponseException.class)
    public ResponseEntity<Object> handleSlackOauthErrorResponseException(SlackOauthErrorResponseException ex) {
        log.info("SlackOauthErrorResponseException : {}", ex.getMessage());

        return createResponseEntity(OauthErrorCode.SLACK_OAUTH_ERROR_RESPONSE);
    }

    @ExceptionHandler(ExpiredSlackOauthStateException.class)
    public ResponseEntity<Object> handleExpiredSlackOauthStateException(ExpiredSlackOauthStateException ex) {
        log.info("ExpiredSlackOauthStateException : {}", ex.getMessage());

        return createResponseEntity(OauthErrorCode.SLACK_OAUTH_EXPIRED_STATE);
    }

    @ExceptionHandler(SlackOauthStateNotFoundException.class)
    public ResponseEntity<Object> handleSlackOauthStateNotFoundException(SlackOauthStateNotFoundException ex) {
        log.info("SlackOauthStateNotFoundException : {}", ex.getMessage());

        return createResponseEntity(OauthErrorCode.SLACK_OAUTH_NOT_FOUND_STATE);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Object> handleInvalidTokenException(InvalidTokenException ex) {
        log.info("InvalidTokenException : {}", ex.getMessage());

        return createResponseEntity(AuthErrorCode.INVALID_TOKEN);
    }

    @ExceptionHandler(EmptyAccessTokenException.class)
    public ResponseEntity<Object> handleEmptyAccessTokenException(EmptyAccessTokenException ex) {
        log.info("EmptyAccessTokenException : {}", ex.getMessage());

        return createResponseEntity(AuthErrorCode.EMPTY_TOKEN);
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<Object> handleWorkspaceNotFoundException(WorkspaceNotFoundException ex) {
        log.info("WorkspaceNotFoundException : {}", ex.getMessage());

        return createResponseEntity(CommandErrorCode.WORK_SPACE_NOT_FOUND);
    }

    @ExceptionHandler(AccessLinkDuplicateKeyException.class)
    public ResponseEntity<Object> handleAccessLinkDuplicateKeyException(AccessLinkDuplicateKeyException ex) {
        log.info("AccessLinkDuplicateKeyException : {}", ex.getMessage());

        return createResponseEntity(CommandErrorCode.DUPLICATE_LINK_KEY);
    }

    @ExceptionHandler(SlackUserInfoRequestException.class)
    public ResponseEntity<Object> handleSlackUserInfoRequestException(SlackUserInfoRequestException ex) {
        log.info("SlackUserInfoRequestException : {}", ex.getMessage());

        return createResponseEntity(CommandErrorCode.SLACK_USER_INFO_API_FAILED);
    }

    @ExceptionHandler(AccessLinkSequenceStateException.class)
    public ResponseEntity<Object> handleAccessLinkSequenceStateException(AccessLinkSequenceStateException ex) {
        log.info("AccessLinkSequenceStateException : {}", ex.getMessage());

        return createResponseEntity(CommandErrorCode.LINK_SEQUENCE_NOT_EXISTS);
    }

    @ExceptionHandler(NotificationSettingsCreationConflictException.class)
    public ResponseEntity<Object> handleNotificationSettingsCreationConflictException(NotificationSettingsCreationConflictException ex) {
        log.info("NotificationSettingsCreationConflictException : {}", ex.getMessage());

        return createResponseEntity(SettingErrorCode.NOTIFICATION_SETTINGS_CONFLICT);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.info("MissingServletRequestParameterException : {}", ex.getMessage());


        return createResponseEntity(DefaultErrorCode.EMPTY_REQUIRED_PARAMETER);
    }

    private ResponseEntity<Object> createResponseEntity(ErrorCode errorCode) {
        ExceptionResponse response = ExceptionResponse.from(errorCode);

        return ResponseEntity.status(errorCode.getHttpStatus())
                             .body(response);
    }
}
