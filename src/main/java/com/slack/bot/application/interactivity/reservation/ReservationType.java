package com.slack.bot.application.interactivity.reservation;

import com.slack.bot.application.interactivity.reservation.dto.ReservationContextDto;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.util.Optional;

public enum ReservationType {
    NEW("리뷰 예약이 완료되었습니다.") {
        @Override
        public ReviewReservation persist(ReviewReservationCoordinator coordinator, ReservationContextDto context) {
            return coordinator.create(context.toReservationCommand());
        }

        @Override
        public boolean isNew() {
            return true;
        }
    },

    RESCHEDULE("리뷰 예약 시간이 변경되었습니다.") {
        @Override
        public ReviewReservation persist(ReviewReservationCoordinator coordinator, ReservationContextDto context) {
            return coordinator.reschedule(context.toReservationCommand());
        }

        @Override
        public boolean isNew() {
            return false;
        }
    };

    private final String successMessage;

    ReservationType(String successMessage) {
        this.successMessage = successMessage;
    }

    public abstract ReviewReservation persist(ReviewReservationCoordinator coordinator, ReservationContextDto context);

    public abstract boolean isNew();

    public String successMessage() {
        return successMessage;
    }

    public static ReservationType resolve(Long reservationId) {
        return Optional.ofNullable(reservationId)
                .map(id -> RESCHEDULE)
                .orElse(NEW);
    }
}
