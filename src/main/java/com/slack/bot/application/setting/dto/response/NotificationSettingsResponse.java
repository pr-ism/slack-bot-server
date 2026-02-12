package com.slack.bot.application.setting.dto.response;

import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.domain.setting.NotificationSettings;

public record NotificationSettingsResponse(
        Long projectMemberId,
        DeliverySpace reservationConfirmedSpace,
        Boolean reservationCanceledConfirmationEnabled,
        Boolean reservationChannelEphemeralEnabled,
        Boolean reviewReminderEnabled,
        Boolean prMentionEnabled,
        Boolean reviewCompletedEnabled
) {

    public static NotificationSettingsResponse from(NotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.getProjectMemberId(),
                settings.getReservationConfirmed().getDeliverySpace(),
                settings.getOptionalNotifications().isReservationCanceledConfirmationEnabled(),
                settings.getOptionalNotifications().isReservationChannelEphemeralEnabled(),
                settings.getOptionalNotifications().isReviewReminderEnabled(),
                settings.getOptionalNotifications().isPullRequestMentionEnabled(),
                settings.getOptionalNotifications().isReviewCompletedEnabled()
        );
    }
}
