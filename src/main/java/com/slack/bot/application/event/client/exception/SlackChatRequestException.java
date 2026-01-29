package com.slack.bot.application.event.client.exception;

public class SlackChatRequestException extends RuntimeException {

    public SlackChatRequestException(String message) {
        super(message);
    }
}
