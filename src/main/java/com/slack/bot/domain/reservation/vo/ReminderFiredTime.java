package com.slack.bot.domain.reservation.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderFiredTime {

    @Column(name = "fired_at")
    private Instant value;

    public static ReminderFiredTime notFired() {
        return new ReminderFiredTime(null);
    }

    public static ReminderFiredTime of(Instant value) {
        if (value == null) {
            return notFired();
        }

        return new ReminderFiredTime(value);
    }

    private ReminderFiredTime(Instant value) {
        this.value = value;
    }

    public boolean isFired() {
        return value != null;
    }
}
