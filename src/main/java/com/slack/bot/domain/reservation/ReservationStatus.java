package com.slack.bot.domain.reservation;

public enum ReservationStatus {
    ACTIVE,
    CANCELLED;

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
