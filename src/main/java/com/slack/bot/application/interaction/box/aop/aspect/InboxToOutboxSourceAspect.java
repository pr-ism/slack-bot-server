package com.slack.bot.application.interaction.box.aop.aspect;

import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.out.OutboxIdempotencySourceContext;
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

    private final ProcessingSourceContext processingSourceContext;
    private final OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    @Around("@annotation(com.slack.bot.application.interaction.box.aop.BindInboxToOutboxSource)")
    public Object bindInboxSource(ProceedingJoinPoint joinPoint) throws Throwable {
        Long inboxId = resolveInboxId(joinPoint.getArgs());
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        processingSourceContext.withInboxProcessing(() -> outboxIdempotencySourceContext.withInboxSource(inboxId, () -> {
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

    private Long resolveInboxId(Object[] args) {
        if (args.length == 0 || args[0] == null) {
            throw new IllegalStateException("인박스 source 바인딩 대상 인자가 없습니다.");
        }

        Object firstArgument = args[0];
        if (firstArgument instanceof SlackInteractionInbox inbox) {
            return inbox.getId();
        }

        if (firstArgument instanceof Long inboxId) {
            return inboxId;
        }

        throw new IllegalStateException("인박스 source 바인딩 대상의 타입이 잘못되었습니다.");
    }
}
