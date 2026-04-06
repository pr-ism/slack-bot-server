package com.slack.bot.application.interaction.box.in.exception;

public class InboxProcessingLeaseLostException extends RuntimeException {

    public InboxProcessingLeaseLostException(String message) {
        super(message);
    }
}
