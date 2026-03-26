package com.slack.bot.application.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PollingHintPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(PollingHintTarget target) {
        applicationEventPublisher.publishEvent(new PollingHintEvent(target));
    }
}
