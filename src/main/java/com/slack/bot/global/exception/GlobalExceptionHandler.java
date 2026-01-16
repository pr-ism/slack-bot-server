package com.slack.bot.global.exception;

import com.slack.bot.global.exception.dto.response.DefaultErrorCode;
import com.slack.bot.global.exception.dto.response.ExceptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

        ExceptionResponse response = ExceptionResponse.from(DefaultErrorCode.UNKNOWN_SERVER_EXCEPTION);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(response);
    }
}
