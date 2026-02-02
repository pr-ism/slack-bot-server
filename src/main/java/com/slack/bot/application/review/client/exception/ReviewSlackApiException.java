package com.slack.bot.application.review.client.exception;

public class ReviewSlackApiException extends RuntimeException {

    public ReviewSlackApiException(String message) {
        super(message);
    }

    public ReviewSlackApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
