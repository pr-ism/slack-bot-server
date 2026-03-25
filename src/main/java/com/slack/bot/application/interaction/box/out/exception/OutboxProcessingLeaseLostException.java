package com.slack.bot.application.interaction.box.out.exception;

public class OutboxProcessingLeaseLostException extends RuntimeException {

    public OutboxProcessingLeaseLostException(String message) {
        super(message);
    }
}
