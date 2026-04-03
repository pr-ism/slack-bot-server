package com.slack.bot.infrastructure.common;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoxHistoryFailureDetail {

    private String failureReason;

    private String failureTypeName;

    public static BoxHistoryFailureDetail of(
            String failureReason,
            Enum<?> failureType
    ) {
        validateFailureReason(failureReason);
        validateFailureType(failureType);

        return new BoxHistoryFailureDetail(failureReason, failureType.name());
    }

    private BoxHistoryFailureDetail(
            String failureReason,
            String failureTypeName
    ) {
        this.failureReason = failureReason;
        this.failureTypeName = failureTypeName;
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
