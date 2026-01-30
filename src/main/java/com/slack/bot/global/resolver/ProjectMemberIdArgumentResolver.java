package com.slack.bot.global.resolver;

import com.slack.bot.global.resolver.dto.ProjectMemberId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class ProjectMemberIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ProjectMemberId.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String paramName = resolveParamName();
        String rawValue = extractParameter(webRequest, paramName);

        validateRequired(rawValue);
        long projectMemberId = parseProjectMemberId(rawValue);
        return new ProjectMemberId(projectMemberId);
    }

    private String resolveParamName() {
        return "projectMemberId";
    }

    private String extractParameter(NativeWebRequest webRequest, String paramName) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }
        return request.getParameter(paramName);
    }

    private void validateRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("프로젝트 멤버의 식별자는 비어 있을 수 없습니다.");
        }
    }

    private long parseProjectMemberId(String value) {
        long parsed = parseLong(value);
        validatePositive(parsed);
        return parsed;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("프로젝트 멤버의 식별자는 숫자여야 합니다.");
        }
    }

    private void validatePositive(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("프로젝트 멤버의 식별자는 0보다 커야 합니다.");
        }
    }
}
