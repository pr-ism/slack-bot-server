package com.slack.bot.application.interactivity.box.aop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionSyncResultDto;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ViewSubmissionInboxEnqueueAspect {

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Around("@annotation(com.slack.bot.application.interactivity.box.aop.EnqueueViewSubmissionInInbox) && args(payload)")
    public Object enqueue(ProceedingJoinPoint joinPoint, JsonNode payload) throws Throwable {
        Object result = joinPoint.proceed();

        if (!(result instanceof ViewSubmissionSyncResultDto syncResultDto)) {
            throw new IllegalStateException(
                    "view_submission enqueue AOP 대상 메서드는 ViewSubmissionSyncResultDto를 반환해야 합니다."
            );
        }

        if (syncResultDto.shouldEnqueue()) {
            slackInteractionInboxProcessor.enqueueViewSubmission(payload.toString());
        }

        return syncResultDto;
    }
}
