package com.slack.bot.application.interactivity.box.aop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.box.aop.exception.ViewSubmissionAopProceedException;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionSyncResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ViewSubmissionInboxEnqueueAspect {

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Around("@annotation(com.slack.bot.application.interactivity.box.aop.EnqueueViewSubmissionInInbox) && args(payload,..)")
    public Object enqueue(ProceedingJoinPoint joinPoint, JsonNode payload) {
        ViewSubmissionSyncResultDto syncResultDto = proceedInViewSubmissionContext(joinPoint);

        if (syncResultDto.shouldEnqueue()) {
            String payloadJson = payload.toString();
            String payloadType = payload.path("type").asText(null);

            try {
                boolean enqueued = slackInteractionInboxProcessor.enqueueViewSubmission(payloadJson);
                if (!enqueued) {
                    log.info("view submission enqueue가 중복 요청으로 스킵되었습니다. payloadType={}", payloadType);
                }
            } catch (Throwable throwable) {
                log.error(
                        "view submission enqueue 처리 중 예외가 발생했습니다. payloadType={}",
                        payloadType,
                        throwable
                );
                throw throwable;
            }
        }

        return syncResultDto;
    }

    private ViewSubmissionSyncResultDto proceedInViewSubmissionContext(ProceedingJoinPoint joinPoint) {
        try {
            return validateSyncResult(joinPoint.proceed());
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }

            throw new ViewSubmissionAopProceedException(throwable);
        }
    }

    private ViewSubmissionSyncResultDto validateSyncResult(Object result) {
        if (!(result instanceof ViewSubmissionSyncResultDto syncResultDto)) {
            throw new IllegalStateException(
                    "view_submission enqueue AOP 대상 메서드는 ViewSubmissionSyncResultDto를 반환해야 합니다."
            );
        }

        return syncResultDto;
    }
}
