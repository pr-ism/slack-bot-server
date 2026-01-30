package com.slack.bot.domain.setting;

import com.slack.bot.domain.common.BaseEntity;
import com.slack.bot.domain.setting.vo.OptionalNotifications;
import com.slack.bot.domain.setting.vo.ReservationConfirmed;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings extends BaseEntity {

    private Long projectMemberId;

    @Embedded
    private ReservationConfirmed reservationConfirmed;

    @Embedded
    private OptionalNotifications optionalNotifications;

    public static NotificationSettings defaults(Long projectMemberId) {
        ReservationConfirmed reservationConfirmed = ReservationConfirmed.defaults();
        OptionalNotifications optionalNotifications = OptionalNotifications.defaults();

        return new NotificationSettings(projectMemberId, reservationConfirmed, optionalNotifications);
    }

    public static NotificationSettings create(
            Long projectMemberId,
            ReservationConfirmed reservationConfirmed,
            OptionalNotifications optionalNotifications
    ) {
        validate(projectMemberId, reservationConfirmed, optionalNotifications);

        return new NotificationSettings(projectMemberId, reservationConfirmed, optionalNotifications);
    }

    private static void validate(
            Long projectMemberId,
            ReservationConfirmed reservationConfirmed,
            OptionalNotifications optionalNotifications
    ) {
        validateProjectMemberId(projectMemberId);
        validateReservationConfirmed(reservationConfirmed);
        validateOptionalNotifications(optionalNotifications);
    }

    private static void validateProjectMemberId(Long projectMemberId) {
        if (projectMemberId == null) {
            throw new IllegalArgumentException("프로젝트 멤버의 식별자는 비어 있을 수 없습니다.");
        }
    }

    private static void validateReservationConfirmed(ReservationConfirmed reservationConfirmed) {
        if (reservationConfirmed == null) {
            throw new IllegalArgumentException("리뷰 예약 확인 설정은 비어 있을 수 없습니다.");
        }
    }

    private static void validateOptionalNotifications(OptionalNotifications optionalNotifications) {
        if (optionalNotifications == null) {
            throw new IllegalArgumentException("알림 설정은 비어 있을 수 없습니다.");
        }
    }

    private NotificationSettings(
            Long projectMemberId,
            ReservationConfirmed reservationConfirmed,
            OptionalNotifications optionalNotifications
    ) {
        this.projectMemberId = projectMemberId;
        this.reservationConfirmed = reservationConfirmed;
        this.optionalNotifications = optionalNotifications;
    }

    public void changeReservationConfirmedSpace(DeliverySpace space) {
        this.reservationConfirmed = reservationConfirmed.changeSpace(space);
    }

    public void updateReservationCanceledConfirmation(boolean enabled) {
        this.optionalNotifications = optionalNotifications.updateReservationCanceledConfirmation(enabled);
    }

    public void updateReviewReminder(boolean enabled) {
        this.optionalNotifications = optionalNotifications.updateReviewReminder(enabled);
    }

    public void updatePrMention(boolean enabled) {
        this.optionalNotifications = optionalNotifications.updatePrMention(enabled);
    }

    public void updateReviewCompleted(boolean enabled) {
        this.optionalNotifications = optionalNotifications.updateReviewCompleted(enabled);
    }
}
