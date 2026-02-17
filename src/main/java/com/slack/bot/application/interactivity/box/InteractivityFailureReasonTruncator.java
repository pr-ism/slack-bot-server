package com.slack.bot.application.interactivity.box;

import org.springframework.stereotype.Component;

@Component
public class InteractivityFailureReasonTruncator {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    public String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        if (reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }

        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
