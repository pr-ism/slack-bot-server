package com.slack.bot.application.review.box.aop.aspect;

import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ReviewNotificationSourceBindingAspect {

    private static final String SOURCE_PREFIX = "REVIEW_REQUEST";

    private final ReviewNotificationSourceContext reviewNotificationSourceContext;

    @Around("@annotation(com.slack.bot.application.review.box.aop.BindReviewNotificationSourceKey) && args(apiKey, request)")
    public Object bindSourceKey(
            ProceedingJoinPoint joinPoint,
            String apiKey,
            ReviewAssignmentRequest request
    ) throws Throwable {
        if (reviewNotificationSourceContext.currentSourceKey().isPresent()) {
            return joinPoint.proceed();
        }

        String sourceKey = buildDefaultSourceKey(apiKey, request);
        try {
            return reviewNotificationSourceContext.withSourceKey(sourceKey, () -> proceed(joinPoint));
        } catch (WrappedThrowable wrapped) {
            throw wrapped.getCause();
        }
    }

    private String buildDefaultSourceKey(String apiKey, ReviewAssignmentRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
        if (request == null || request.githubPullRequestId() == null || request.githubPullRequestId() <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }

        return SOURCE_PREFIX + ":" + apiKey + ":" + request.githubPullRequestId();
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable throwable) {
            throw new WrappedThrowable(throwable);
        }
    }

    private static class WrappedThrowable extends RuntimeException {

        private WrappedThrowable(Throwable cause) {
            super(cause);
        }
    }
}
