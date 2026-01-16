package com.slack.bot.docs.snippet.exceptions;

import com.slack.bot.global.exception.dto.response.ErrorCode;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import java.util.Map;

public abstract class CommonExceptionController extends CommonControllerSliceTestSupport {

    protected void registerExceptionContent(Map<String, ExceptionContent> target, ErrorCode... errorCodes) {
        for (ErrorCode errorCode : errorCodes) {
            ExceptionContent exceptionContent = new ExceptionContent(errorCode.getHttpStatus(), errorCode.getMessage());

            target.put(errorCode.getErrorCode(), exceptionContent);
        }
    }
}
