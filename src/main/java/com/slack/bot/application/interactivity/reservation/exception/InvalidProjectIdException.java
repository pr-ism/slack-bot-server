package com.slack.bot.application.interactivity.reservation.exception;

public class InvalidProjectIdException extends RuntimeException {

    public InvalidProjectIdException(String rawProjectId, Throwable cause) {
        super("잘못된 project_id 값입니다: " + rawProjectId, cause);
    }

    public InvalidProjectIdException(String rawProjectId) {
        this(rawProjectId, null);
    }
}
