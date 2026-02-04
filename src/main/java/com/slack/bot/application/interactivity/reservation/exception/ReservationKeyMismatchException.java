package com.slack.bot.application.interactivity.reservation.exception;

public class ReservationKeyMismatchException extends RuntimeException {

    public ReservationKeyMismatchException(String message) {
        super(message);
    }
}
