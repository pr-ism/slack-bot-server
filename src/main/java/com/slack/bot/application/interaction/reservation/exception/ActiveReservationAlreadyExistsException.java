package com.slack.bot.application.interaction.reservation.exception;

public class ActiveReservationAlreadyExistsException extends RuntimeException {

    public ActiveReservationAlreadyExistsException(String message) {
        super(message);
    }
}
