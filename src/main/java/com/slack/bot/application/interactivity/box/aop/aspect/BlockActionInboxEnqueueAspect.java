package com.slack.bot.application.interactivity.box.aop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.box.aop.exception.BlockActionAopProceedException;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
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
public class BlockActionInboxEnqueueAspect {

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;
    private final OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    @Around("@annotation(com.slack.bot.application.interactivity.box.aop.EnqueueBlockActionInInbox) && args(payload,..)")
    public Object enqueue(ProceedingJoinPoint joinPoint, JsonNode payload) {
        if (outboxIdempotencySourceContext.currentSourceKey().isPresent()) {
            return proceedInInboxContext(joinPoint);
        }

        String payloadJson = payload.toString();
        String payloadType = payload.path("type").asText(null);

        try {
            boolean enqueued = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
            if (!enqueued) {
                log.info("block action enqueue가 중복 요청으로 스킵되었습니다. payloadType={}", payloadType);
            }
        } catch (RuntimeException runtimeException) {
            log.error(
                    "block action enqueue 처리 중 예외가 발생했습니다. payloadType={}",
                    payloadType,
                    runtimeException
            );
            throw runtimeException;
        }

        return null;
    }

    private Object proceedInInboxContext(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }

            throw new BlockActionAopProceedException("INBOX_SOURCE_BOUND", throwable);
        }
    }
}
