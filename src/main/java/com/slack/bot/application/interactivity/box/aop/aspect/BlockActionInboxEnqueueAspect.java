package com.slack.bot.application.interactivity.box.aop.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class BlockActionInboxEnqueueAspect {

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Around("@annotation(com.slack.bot.application.interactivity.box.aop.EnqueueBlockActionInInbox) && args(payload)")
    public Object enqueue(JsonNode payload) {
        slackInteractionInboxProcessor.enqueueBlockAction(payload.toString());
        return null;
    }
}
