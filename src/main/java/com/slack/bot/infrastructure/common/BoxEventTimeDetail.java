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
public class BoxEventTimeDetail {

    @Enumerated(EnumType.STRING)
    private BoxDetailOwnerType ownerType;

    @Enumerated(EnumType.STRING)
    private BoxEventKind eventKind;

    private Instant occurredAt;

    public static BoxEventTimeDetail of(
            BoxDetailOwnerType ownerType,
            BoxEventKind eventKind,
            Instant occurredAt
    ) {
        validateOwnerType(ownerType);
        validateEventKind(eventKind);
        validateOccurredAt(occurredAt);

        return new BoxEventTimeDetail(ownerType, eventKind, occurredAt);
    }

    private BoxEventTimeDetail(
            BoxDetailOwnerType ownerType,
            BoxEventKind eventKind,
            Instant occurredAt
    ) {
        this.ownerType = ownerType;
        this.eventKind = eventKind;
        this.occurredAt = occurredAt;
    }

    private static void validateOwnerType(BoxDetailOwnerType ownerType) {
        if (ownerType == null) {
            throw new IllegalArgumentException("ownerType은 비어 있을 수 없습니다.");
        }
    }

    private static void validateEventKind(BoxEventKind eventKind) {
        if (eventKind == null) {
            throw new IllegalArgumentException("eventKind는 비어 있을 수 없습니다.");
        }
    }

    private static void validateOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 비어 있을 수 없습니다.");
        }
    }
}
