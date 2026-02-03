package com.slack.bot.application.interactivity.block;

import java.util.Arrays;

public enum BlockActionType {

    CLAIM_PREFIX("claim_", true),
    OPEN_REVIEW_SCHEDULER("open_review_scheduler", false),
    CANCEL_REVIEW_RESERVATION("cancel_review_reservation", false),
    CHANGE_REVIEW_RESERVATION("change_review_reservation", false),
    UNKNOWN("", false);

    private final String value;
    private final boolean prefixMatch;

    BlockActionType(String value, boolean prefixMatch) {
        this.value = value;
        this.prefixMatch = prefixMatch;
    }

    public static BlockActionType from(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return UNKNOWN;
        }

        return Arrays.stream(values())
                     .filter(candidate -> candidate.matches(actionId))
                     .findFirst()
                     .orElse(UNKNOWN);
    }

    public boolean matches(String actionId) {
        if (isUnknown()) {
            return false;
        }
        if (prefixMatch) {
            return actionId.startsWith(value);
        }
        return value.equals(actionId);
    }

    public String value() {
        return value;
    }

    public boolean isClaimPrefix() {
        return this == CLAIM_PREFIX;
    }

    public boolean isOpenReviewScheduler() {
        return this == OPEN_REVIEW_SCHEDULER;
    }

    public boolean isCancelReviewReservation() {
        return this == CANCEL_REVIEW_RESERVATION;
    }

    public boolean isChangeReviewReservation() {
        return this == CHANGE_REVIEW_RESERVATION;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }
}
