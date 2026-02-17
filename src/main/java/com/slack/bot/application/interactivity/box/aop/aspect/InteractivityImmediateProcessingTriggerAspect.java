package com.slack.bot.application.interactivity.box.aop.aspect;

import com.slack.bot.application.interactivity.box.InteractivityImmediateProcessor;
import com.slack.bot.application.interactivity.box.aop.InteractivityImmediateTriggerTarget;
import com.slack.bot.application.interactivity.box.aop.TriggerInteractivityImmediateProcessing;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class InteractivityImmediateProcessingTriggerAspect {

    private final InteractivityImmediateProcessor interactivityImmediateProcessor;

    @AfterReturning(
            value = "@annotation(triggerInteractivityImmediateProcessing)",
            returning = "result"
    )
    public void trigger(
            TriggerInteractivityImmediateProcessing triggerInteractivityImmediateProcessing,
            Object result
    ) {
        if (triggerInteractivityImmediateProcessing.onlyWhenEnqueued()) {
            validateBooleanReturn(result);

            if (Boolean.FALSE.equals(result)) {
                return;
            }
        }

        InteractivityImmediateTriggerTarget target = triggerInteractivityImmediateProcessing.value();

        if (target == InteractivityImmediateTriggerTarget.BLOCK_ACTION_INBOX) {
            interactivityImmediateProcessor.triggerBlockActionInbox();
            return;
        }
        if (target == InteractivityImmediateTriggerTarget.VIEW_SUBMISSION_INBOX) {
            interactivityImmediateProcessor.triggerViewSubmissionInbox();
            return;
        }
        if (target == InteractivityImmediateTriggerTarget.OUTBOX) {
            interactivityImmediateProcessor.triggerOutbox();
        }
    }

    private void validateBooleanReturn(Object result) {
        if (result instanceof Boolean) {
            return;
        }

        throw new IllegalStateException("onlyWhenEnqueued=true 인 메서드는 boolean 반환 타입이어야 합니다.");
    }
}
