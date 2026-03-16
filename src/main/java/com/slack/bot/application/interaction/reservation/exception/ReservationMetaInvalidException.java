package com.slack.bot.application.interaction.reservation.exception;

public class ReservationMetaInvalidException extends IllegalArgumentException {

    public ReservationMetaInvalidException(String message) {
        super(message);
    }

    public ReservationMetaInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
