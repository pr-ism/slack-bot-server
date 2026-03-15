package com.slack.bot.application.interaction.reservation.exception;

public class ReservationKeyMismatchException extends RuntimeException {

    public ReservationKeyMismatchException(String message) {
        super(message);
    }
}
