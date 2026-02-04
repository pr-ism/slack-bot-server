package com.slack.bot.application.interactivity.reservation.exception;

public class ActiveReservationAlreadyExistsException extends RuntimeException {

    public ActiveReservationAlreadyExistsException(String message) {
        super(message);
    }
}
