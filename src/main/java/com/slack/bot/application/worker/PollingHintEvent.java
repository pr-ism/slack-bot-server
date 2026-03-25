package com.slack.bot.application.worker;

import java.util.Objects;

public record PollingHintEvent(PollingHintTarget target) {

    public PollingHintEvent {
        Objects.requireNonNull(target, "target은 비어 있을 수 없습니다.");
    }
}
