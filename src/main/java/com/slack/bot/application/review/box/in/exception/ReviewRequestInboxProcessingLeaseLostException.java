package com.slack.bot.application.review.box.in.exception;

public class ReviewRequestInboxProcessingLeaseLostException extends RuntimeException {

    public ReviewRequestInboxProcessingLeaseLostException(String message) {
        super(message);
    }
}
