package com.slack.bot.infrastructure.common;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoxHistoryFailureDetail {

    @Enumerated(EnumType.STRING)
    private BoxDetailOwnerType ownerType;

    private String failureReason;

    private String failureTypeName;

    public static BoxHistoryFailureDetail of(
            BoxDetailOwnerType ownerType,
            String failureReason,
            Enum<?> failureType
    ) {
        validateOwnerType(ownerType);
        validateFailureReason(failureReason);
        validateFailureType(failureType);

        return new BoxHistoryFailureDetail(ownerType, failureReason, failureType.name());
    }

    private BoxHistoryFailureDetail(
            BoxDetailOwnerType ownerType,
            String failureReason,
            String failureTypeName
    ) {
        this.ownerType = ownerType;
        this.failureReason = failureReason;
        this.failureTypeName = failureTypeName;
    }

    private static void validateOwnerType(BoxDetailOwnerType ownerType) {
        if (ownerType == null) {
            throw new IllegalArgumentException("ownerType은 비어 있을 수 없습니다.");
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
