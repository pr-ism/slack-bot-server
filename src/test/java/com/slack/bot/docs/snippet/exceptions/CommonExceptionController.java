package com.slack.bot.docs.snippet.exceptions;

import com.slack.bot.global.exception.dto.response.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class CommonExceptionController {

    protected Map<String, ExceptionContent> registerExceptionContent(ErrorCode... errorCodes) {
        Map<String, ExceptionContent> exceptionContents = new LinkedHashMap<>();

        for (ErrorCode errorCode : errorCodes) {
            ExceptionContent exceptionContent = new ExceptionContent(errorCode.getHttpStatus(), errorCode.getMessage());

            exceptionContents.put(errorCode.getErrorCode(), exceptionContent);
        }

        return exceptionContents;
    }
}
