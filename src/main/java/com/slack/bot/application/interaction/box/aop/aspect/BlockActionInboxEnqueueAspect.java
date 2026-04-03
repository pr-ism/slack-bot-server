package com.slack.bot.application.interaction.box.aop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interaction.block.BlockActionType;
import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.aop.aspect.exception.BlockActionAopProceedException;
import com.slack.bot.application.interaction.box.in.SlackInteractionInboxProcessor;
import java.util.Optional;
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
    private final ProcessingSourceContext processingSourceContext;

    @Around("@annotation(com.slack.bot.application.interaction.box.aop.EnqueueBlockActionInInbox) && args(payload,..)")
    public void enqueue(ProceedingJoinPoint joinPoint, JsonNode payload) {
        if (processingSourceContext.isInboxProcessing() || isSyncAction(payload)) {
            proceedInInboxContext(joinPoint);
            return;
        }

        String payloadJson = payload.toString();

        try {
            boolean enqueued = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
            if (!enqueued) {
                logDuplicateSkip(payload.path("type"));
            }
        } catch (RuntimeException runtimeException) {
            logEnqueueFailure(payload.path("type"), runtimeException);
            throw runtimeException;
        }
    }

    private boolean isSyncAction(JsonNode payload) {
        return resolveActionType(payload)
                .map(actionType -> actionType.isOpenReviewScheduler() || actionType.isChangeReviewReservation())
                .orElse(false);
    }

    private Optional<BlockActionType> resolveActionType(JsonNode payload) {
        return resolveActionId(payload)
                .map(actionId -> BlockActionType.from(actionId));
    }

    private Optional<String> resolveActionId(JsonNode payload) {
        return resolveText(
                payload.path("actions")
                       .path(0)
                       .path("action_id")
        ).filter(actionId -> !actionId.isBlank())
         .or(() -> resolveText(payload.path("action_id")));
    }

    private Optional<String> resolveText(JsonNode node) {
        return Optional.of(node)
                .filter(currentNode -> !currentNode.isMissingNode())
                .filter(currentNode -> !currentNode.isNull())
                .filter(currentNode -> currentNode.isValueNode())
                .map(currentNode -> currentNode.asText());
    }

    private void logDuplicateSkip(JsonNode payloadTypeNode) {
        resolveText(payloadTypeNode)
                .filter(value -> !value.isBlank())
                .ifPresentOrElse(
                value -> log.info("block action enqueue가 중복 요청으로 스킵되었습니다. payloadType={}", value),
                () -> log.info("block action enqueue가 중복 요청으로 스킵되었습니다. payloadType 정보가 없습니다.")
        );
    }

    private void logEnqueueFailure(JsonNode payloadTypeNode, RuntimeException runtimeException) {
        resolveText(payloadTypeNode)
                .filter(value -> !value.isBlank())
                .ifPresentOrElse(
                value -> log.error(
                        "block action enqueue 처리 중 예외가 발생했습니다. payloadType={}",
                        value,
                        runtimeException
                ),
                () -> log.error(
                        "block action enqueue 처리 중 예외가 발생했습니다. payloadType 정보가 없습니다.",
                        runtimeException
                )
        );
    }

    private void proceedInInboxContext(ProceedingJoinPoint joinPoint) {
        try {
            joinPoint.proceed();
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }

            throw new BlockActionAopProceedException(throwable);
        }
    }
}
