package com.slack.bot.infrastructure.common;

import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoxEventTimeDetail {

    private Instant occurredAt;

    public static BoxEventTimeDetail of(Instant occurredAt) {
        validateOccurredAt(occurredAt);

        return new BoxEventTimeDetail(occurredAt);
    }

    private BoxEventTimeDetail(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    private static void validateOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 비어 있을 수 없습니다.");
        }
    }
}
