package com.slack.bot.application.interaction.block.dto;

import com.slack.bot.domain.reservation.ReviewReservation;

public record BlockActionOutcomeDto(ReviewReservation duplicateReservation, ReviewReservation cancelledReservation) {

    public static BlockActionOutcomeDto empty() {
        return new BlockActionOutcomeDto(null, null);
    }

    public static BlockActionOutcomeDto duplicate(ReviewReservation reservation) {
        return new BlockActionOutcomeDto(reservation, null);
    }

    public static BlockActionOutcomeDto cancelled(ReviewReservation reservation) {
        return new BlockActionOutcomeDto(null, reservation);
    }

    public boolean hasDuplicateReservation() {
        return duplicateReservation != null;
    }

    public boolean hasCancelledReservation() {
        return cancelledReservation != null;
    }
}
