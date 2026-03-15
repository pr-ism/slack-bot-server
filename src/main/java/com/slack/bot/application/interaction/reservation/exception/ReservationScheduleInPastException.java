package com.slack.bot.application.interaction.reservation.exception;

public class ReservationScheduleInPastException extends RuntimeException {

    public ReservationScheduleInPastException(String message) {
        super(message);
    }
}
