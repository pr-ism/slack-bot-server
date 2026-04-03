package com.slack.bot.infrastructure.common;

import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoxFailureDetail {

    private Instant failedAt;

    private String failureReason;

    private String failureTypeName;

    public static BoxFailureDetail of(
            Instant failedAt,
            String failureReason,
            Enum<?> failureType
    ) {
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateFailureType(failureType);

        return new BoxFailureDetail(failedAt, failureReason, failureType.name());
    }

    private BoxFailureDetail(
            Instant failedAt,
            String failureReason,
            String failureTypeName
    ) {
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureTypeName = failureTypeName;
    }

    private static void validateFailedAt(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private static void validateFailureType(Enum<?> failureType) {
        if (failureType == null) {
            throw new IllegalArgumentException("failureType은 비어 있을 수 없습니다.");
        }
    }
}
