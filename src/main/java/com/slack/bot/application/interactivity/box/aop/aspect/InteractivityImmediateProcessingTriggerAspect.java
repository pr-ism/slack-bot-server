package com.slack.bot.application.interactivity.box.aop.aspect;

import com.slack.bot.application.interactivity.box.InteractionImmediateProcessor;
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

    private final InteractionImmediateProcessor interactionImmediateProcessor;

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
        target.trigger(interactionImmediateProcessor);
    }

    private void validateBooleanReturn(Object result) {
        if (result instanceof Boolean) {
            return;
        }

        throw new IllegalStateException("onlyWhenEnqueued=true 인 메서드는 boolean 반환 타입이어야 합니다.");
    }
}
