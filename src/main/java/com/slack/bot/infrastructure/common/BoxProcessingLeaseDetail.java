package com.slack.bot.infrastructure.common;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoxProcessingLeaseDetail {

    @Enumerated(EnumType.STRING)
    private BoxDetailOwnerType ownerType;

    private Instant startedAt;

    public static BoxProcessingLeaseDetail of(
            BoxDetailOwnerType ownerType,
            Instant startedAt
    ) {
        validateOwnerType(ownerType);
        validateStartedAt(startedAt);

        return new BoxProcessingLeaseDetail(ownerType, startedAt);
    }

    private BoxProcessingLeaseDetail(
            BoxDetailOwnerType ownerType,
            Instant startedAt
    ) {
        this.ownerType = ownerType;
        this.startedAt = startedAt;
    }

    public void updateStartedAt(Instant startedAt) {
        validateStartedAt(startedAt);

        this.startedAt = startedAt;
    }

    private static void validateOwnerType(BoxDetailOwnerType ownerType) {
        if (ownerType == null) {
            throw new IllegalArgumentException("ownerType은 비어 있을 수 없습니다.");
        }
    }

    private static void validateStartedAt(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt은 비어 있을 수 없습니다.");
        }
    }
}
