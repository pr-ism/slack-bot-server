package com.slack.bot.application.interactivity.view;

import java.util.Arrays;

public enum ViewCallbackId {
    REVIEW_TIME_SUBMIT("review_time_submit"),
    REVIEW_TIME_CUSTOM_SUBMIT("review_time_custom_submit"),
    UNKNOWN(null);

    private final String value;

    ViewCallbackId(String value) {
        this.value = value;
    }

    public static ViewCallbackId from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                     .filter(v -> v.value != null && v.value.equals(raw))
                     .findFirst()
                     .orElse(UNKNOWN);
    }

    public boolean isReviewTimeSubmit() {
        return this == REVIEW_TIME_SUBMIT;
    }

    public boolean isReviewTimeCustomSubmit() {
        return this == REVIEW_TIME_CUSTOM_SUBMIT;
    }

    public String value() {
        return value;
    }
}
