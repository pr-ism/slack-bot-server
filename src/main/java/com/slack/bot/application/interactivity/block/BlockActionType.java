package com.slack.bot.application.interactivity.block;

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

        BlockActionType exactMatch = findExactMatch(actionId);

        if (exactMatch != UNKNOWN) {
            return exactMatch;
        }

        return findBestPrefixMatch(actionId);
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

    private boolean matchesExactly(String actionId) {
        return !prefixMatch && !isUnknown() && value.equals(actionId);
    }

    private boolean isPrefixMatchType() {
        return prefixMatch && !isUnknown();
    }

    private static BlockActionType findExactMatch(String actionId) {
        for (BlockActionType candidate : values()) {
            if (candidate.matchesExactly(actionId)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }

    private static BlockActionType findBestPrefixMatch(String actionId) {
        BlockActionType bestPrefixMatch = UNKNOWN;

        for (BlockActionType candidate : values()) {
            bestPrefixMatch = selectLongerPrefixMatch(bestPrefixMatch, candidate, actionId);
        }

        return bestPrefixMatch;
    }

    private static BlockActionType selectLongerPrefixMatch(
            BlockActionType currentBest,
            BlockActionType candidate,
            String actionId
    ) {
        if (!candidate.isPrefixMatchType() || !candidate.matches(actionId)) {
            return currentBest;
        }

        if (candidate.value.length() <= currentBest.value.length()) {
            return currentBest;
        }

        return candidate;
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
