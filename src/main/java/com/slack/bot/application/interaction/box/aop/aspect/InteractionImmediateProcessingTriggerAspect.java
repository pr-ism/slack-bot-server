package com.slack.bot.application.interaction.box.aop.aspect;

import com.slack.bot.application.interaction.box.InteractionImmediateProcessor;
import com.slack.bot.application.interaction.box.aop.InteractionImmediateTriggerTarget;
import com.slack.bot.application.interaction.box.aop.TriggerInteractionImmediateProcessing;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class InteractionImmediateProcessingTriggerAspect {

    private final InteractionImmediateProcessor interactionImmediateProcessor;

    @AfterReturning(
            value = "@annotation(triggerInteractionImmediateProcessing)",
            returning = "result"
    )
    public void trigger(
            TriggerInteractionImmediateProcessing triggerInteractionImmediateProcessing,
            Object result
    ) {
        if (triggerInteractionImmediateProcessing.onlyWhenEnqueued()) {
            validateBooleanReturn(result);

            if (Boolean.FALSE.equals(result)) {
                return;
            }
        }

        InteractionImmediateTriggerTarget target = triggerInteractionImmediateProcessing.value();
        target.trigger(interactionImmediateProcessor);
    }

    private void validateBooleanReturn(Object result) {
        if (result instanceof Boolean) {
            return;
        }

        throw new IllegalStateException("onlyWhenEnqueued=true 인 메서드는 boolean 반환 타입이어야 합니다.");
    }
}
