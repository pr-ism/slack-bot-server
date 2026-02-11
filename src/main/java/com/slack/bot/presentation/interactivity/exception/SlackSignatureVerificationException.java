package com.slack.bot.presentation.interactivity.exception;

public class SlackSignatureVerificationException extends RuntimeException {

    public SlackSignatureVerificationException(String message) {
        super(message);
    }
}
