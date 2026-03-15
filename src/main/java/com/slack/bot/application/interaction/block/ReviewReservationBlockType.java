package com.slack.bot.application.interaction.block;

public enum ReviewReservationBlockType {
    RESERVATION,
    CANCELLATION;

    public boolean isReservation() {
        return this == RESERVATION;
    }

    public boolean isCancellation() {
        return this == CANCELLATION;
    }
}
