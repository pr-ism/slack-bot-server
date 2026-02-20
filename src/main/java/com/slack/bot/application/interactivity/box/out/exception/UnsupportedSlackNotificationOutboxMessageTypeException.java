package com.slack.bot.application.interactivity.box.out.exception;

import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;

public class UnsupportedSlackNotificationOutboxMessageTypeException extends RuntimeException {

    public UnsupportedSlackNotificationOutboxMessageTypeException(SlackNotificationOutboxMessageType messageType) {
        super("지원하지 않는 메시지 타입입니다: " + messageType);
    }
}
