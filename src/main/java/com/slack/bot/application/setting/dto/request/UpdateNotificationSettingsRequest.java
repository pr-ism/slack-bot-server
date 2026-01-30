package com.slack.bot.application.setting.dto.request;

import com.slack.bot.domain.setting.DeliverySpace;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(

        @NotNull(message = "예약 완료 확인 알림 전달 공간은 필수입니다.")
        DeliverySpace reservationConfirmedSpace,

        boolean reservationCanceledConfirmationEnabled,
        boolean reviewReminderEnabled,
        boolean prMentionEnabled,
        boolean reviewCompletedEnabled
) {
}
