package com.slack.bot.domain.analysis.metadata.reservation.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReservationInteractionCount {

    private int scheduleCancelCount;
    private int scheduleChangeCount;

    public static ReviewReservationInteractionCount defaults() {
        return create(0, 0);
    }

    public static ReviewReservationInteractionCount create(int scheduleCancelCount, int scheduleChangeCount) {
        validateScheduleCancelCount(scheduleCancelCount);
        validateScheduleChangeCount(scheduleChangeCount);

        return new ReviewReservationInteractionCount(scheduleCancelCount, scheduleChangeCount);
    }

    private ReviewReservationInteractionCount(int scheduleCancelCount, int scheduleChangeCount) {
        this.scheduleCancelCount = scheduleCancelCount;
        this.scheduleChangeCount = scheduleChangeCount;
    }

    public void increaseScheduleCancelCount() {
        this.scheduleCancelCount = this.scheduleCancelCount + 1;
    }

    public void increaseScheduleChangeCount() {
        this.scheduleChangeCount = this.scheduleChangeCount + 1;
    }

    private static void validateScheduleCancelCount(int scheduleCancelCount) {
        if (scheduleCancelCount < 0) {
            throw new IllegalArgumentException("예약 취소 횟수는 음수일 수 없습니다.");
        }
    }

    private static void validateScheduleChangeCount(int scheduleChangeCount) {
        if (scheduleChangeCount < 0) {
            throw new IllegalArgumentException("예약 변경 횟수는 음수일 수 없습니다.");
        }
    }
}
