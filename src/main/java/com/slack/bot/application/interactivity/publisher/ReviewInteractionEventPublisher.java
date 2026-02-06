package com.slack.bot.application.interactivity.publisher;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewInteractionEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(ReviewInteractionEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
