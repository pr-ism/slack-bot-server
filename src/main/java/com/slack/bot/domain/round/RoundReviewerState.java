package com.slack.bot.domain.round;

public enum RoundReviewerState {
    REQUESTED,
    REVIEWED;

    public boolean isRequested() {
        return this == REQUESTED;
    }

    public boolean isReviewed() {
        return this == REVIEWED;
    }

    public RoundReviewerState request() {
        return REQUESTED;
    }

    public RoundReviewerState review() {
        return REVIEWED;
    }
}
