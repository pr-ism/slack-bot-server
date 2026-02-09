package com.slack.bot.application.interactivity.block.dto;

import com.slack.bot.domain.reservation.ReviewReservation;

public record BlockActionOutcomeDto(ReviewReservation duplicateReservation, ReviewReservation cancelledReservation) {

    public static BlockActionOutcomeDto empty() {
        return new BlockActionOutcomeDto(null, null);
    }

    public boolean hasDuplicateReservation() {
        return duplicateReservation != null;
    }

    public boolean hasCancelledReservation() {
        return cancelledReservation != null;
    }
}
