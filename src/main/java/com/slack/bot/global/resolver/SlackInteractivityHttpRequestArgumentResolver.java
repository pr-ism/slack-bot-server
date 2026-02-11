package com.slack.bot.global.resolver;

import com.slack.bot.presentation.interactivity.dto.request.SlackInteractivityHttpRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class SlackInteractivityHttpRequestArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SlackInteractivityHttpRequest.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        if (request == null) {
            throw new IllegalStateException("요청 객체를 찾을 수 없습니다.");
        }

        return parse(request);
    }

    private SlackInteractivityHttpRequest parse(HttpServletRequest request) {
        String timestamp = request.getHeader("X-Slack-Request-Timestamp");
        String signature = request.getHeader("X-Slack-Signature");
        String rawBody = readBody(request);
        String payloadJson = extractPayloadJson(rawBody);

        return new SlackInteractivityHttpRequest(timestamp, signature, rawBody, payloadJson);
    }

    private String readBody(HttpServletRequest request) {
        try {
            return StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("슬랙 인터랙티브 요청 바디를 읽을 수 없습니다.", e);
        }
    }

    private String extractPayloadJson(String rawBody) {
        if (rawBody == null) {
            return null;
        }

        int idx = rawBody.indexOf("payload=");
        String encoded = rawBody;

        if (idx >= 0) {
            encoded = rawBody.substring(idx + "payload=".length());
        }

        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
