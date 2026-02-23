package com.slack.bot.application.interactivity.box.aop.aspect;

import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class OutboxSourceResolverAspect {

    private final OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    @Around("@annotation(com.slack.bot.application.interactivity.box.aop.ResolveOutboxSource)")
    public Object resolveSource(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        if (args.length == 0) {
            throw new IllegalStateException("아웃박스 source 키 주입 대상 인자가 없습니다.");
        }

        if (args[0] != null && !(args[0] instanceof String)) {
            throw new IllegalStateException("아웃박스 source 키 주입 대상의 타입이 잘못되었습니다.");
        }

        if (args[0] != null) {
            return joinPoint.proceed();
        }

        args[0] = outboxIdempotencySourceContext.requireSourceKey();
        return joinPoint.proceed(args);
    }
}
