package com.slack.bot.docs.snippet.exceptions.oauth;

import com.slack.bot.docs.snippet.dto.response.CommonDocsResponse;
import com.slack.bot.docs.snippet.exceptions.CommonExceptionController;
import com.slack.bot.docs.snippet.exceptions.ExceptionContent;
import com.slack.bot.global.exception.dto.response.AuthErrorCode;
import com.slack.bot.global.exception.dto.response.DefaultErrorCode;
import com.slack.bot.global.exception.dto.response.OauthErrorCode;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/oauth")
public class OauthExceptionController extends CommonExceptionController {

    @GetMapping("/exceptions")
    public ResponseEntity<CommonDocsResponse<OauthExceptionDocs>> findExceptions() {
        OauthExceptionDocs oauthExceptionDocs =
                OauthExceptionDocs.builder()
                                  .installException(registerInstallException())
                                  .callbackException(registerCallbackException())
                                  .build();

        return ResponseEntity.ok(new CommonDocsResponse<>(oauthExceptionDocs));
    }

    private Map<String, ExceptionContent> registerInstallException() {
        return registerExceptionContent(
                DefaultErrorCode.EMPTY_REQUIRED_PARAMETER,
                AuthErrorCode.INVALID_TOKEN,
                AuthErrorCode.EMPTY_TOKEN
        );
    }

    private Map<String, ExceptionContent> registerCallbackException() {
        return registerExceptionContent(
                OauthErrorCode.SLACK_OAUTH_EMPTY_RESPONSE,
                OauthErrorCode.SLACK_OAUTH_ERROR_RESPONSE,
                OauthErrorCode.SLACK_OAUTH_EXPIRED_STATE,
                OauthErrorCode.SLACK_OAUTH_NOT_FOUND_STATE
        );
    }
}
