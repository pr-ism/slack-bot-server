package com.slack.bot.application.interactivity.box.aop.aspect;

import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interactivity.box.ProcessingSourceContext;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class InboxToOutboxSourceAspect {

    private final OutboxIdempotencySourceContext outboxIdempotencySourceContext;
    private final ProcessingSourceContext processingSourceContext;

    @Around("@annotation(com.slack.bot.application.interactivity.box.aop.BindInboxToOutboxSource) && args(inbox,..)")
    public Object bindInboxSource(ProceedingJoinPoint joinPoint, SlackInteractionInbox inbox) throws Throwable {
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        processingSourceContext.withInboxProcessing(() -> outboxIdempotencySourceContext.withInboxSource(inbox.getId(), () -> {
            try {
                result.set(joinPoint.proceed());
            } catch (Throwable error) {
                throwable.set(error);
            }
        }));

        Throwable error = throwable.get();
        if (error != null) {
            throw error;
        }

        return result.get();
    }
}
