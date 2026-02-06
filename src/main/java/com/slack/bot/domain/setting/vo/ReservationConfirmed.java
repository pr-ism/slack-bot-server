package com.slack.bot.domain.setting.vo;

import com.slack.bot.domain.setting.DeliverySpace;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ReservationConfirmed {

    @Enumerated(EnumType.STRING)
    private DeliverySpace deliverySpace;

    public static ReservationConfirmed defaults() {
        return new ReservationConfirmed(DeliverySpace.DIRECT_MESSAGE);
    }

    private ReservationConfirmed(DeliverySpace deliverySpace) {
        this.deliverySpace = deliverySpace;
    }

    public ReservationConfirmed changeSpace(DeliverySpace newSpace) {
        if (newSpace == null) {
            throw new IllegalArgumentException("알림이 전달된 장소는 비어 있을 수 없습니다.");
        }

        return new ReservationConfirmed(newSpace);
    }

    public boolean isDirectMessageEnabled() {
        return this.deliverySpace.isDirectMessage();
    }
}
