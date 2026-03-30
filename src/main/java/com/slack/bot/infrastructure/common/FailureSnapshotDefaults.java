package com.slack.bot.infrastructure.common;

import java.time.Instant;

public final class FailureSnapshotDefaults {

    public static final Instant NO_PROCESSING_STARTED_AT = Instant.EPOCH;

    public static final Instant NO_PROCESSED_AT = Instant.EPOCH;

    public static final Instant NO_SENT_AT = Instant.EPOCH;

    public static final Instant NO_FAILURE_AT = Instant.EPOCH;

    public static final String NO_FAILURE_REASON = "";

    private FailureSnapshotDefaults() {
    }
}
