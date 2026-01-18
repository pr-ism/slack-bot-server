package com.slack.bot.docs.snippet.exceptions.oauth;

import com.slack.bot.docs.snippet.exceptions.ExceptionContent;
import java.util.Map;
import lombok.Builder;

@Builder
public record OauthExceptionDocs(Map<String, ExceptionContent> callbackException) {
}
