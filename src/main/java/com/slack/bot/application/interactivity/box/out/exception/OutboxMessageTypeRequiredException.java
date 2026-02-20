package com.slack.bot.application.interactivity.box.out.exception;

public class OutboxMessageTypeRequiredException extends IllegalArgumentException {

    public OutboxMessageTypeRequiredException() {
        super("messageType은 비어 있을 수 없습니다.");
    }
}
