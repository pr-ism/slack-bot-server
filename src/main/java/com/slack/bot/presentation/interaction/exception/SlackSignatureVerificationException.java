package com.slack.bot.presentation.interaction.exception;

public class SlackSignatureVerificationException extends RuntimeException {

    public SlackSignatureVerificationException(String message) {
        super(message);
    }
}
