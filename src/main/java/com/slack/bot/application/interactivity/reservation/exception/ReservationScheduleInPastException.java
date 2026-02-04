package com.slack.bot.application.interactivity.reservation.exception;

public class ReservationScheduleInPastException extends RuntimeException {

    public ReservationScheduleInPastException(String message) {
        super(message);
    }
}
