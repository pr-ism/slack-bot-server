package com.slack.bot.infrastructure.common;

import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoxProcessingLeaseDetail {

    private Instant startedAt;

    public static BoxProcessingLeaseDetail of(Instant startedAt) {
        validateStartedAt(startedAt);

        return new BoxProcessingLeaseDetail(startedAt);
    }

    private BoxProcessingLeaseDetail(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void updateStartedAt(Instant startedAt) {
        validateStartedAt(startedAt);
        this.startedAt = startedAt;
    }

    private static void validateStartedAt(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt은 비어 있을 수 없습니다.");
        }
    }
}
