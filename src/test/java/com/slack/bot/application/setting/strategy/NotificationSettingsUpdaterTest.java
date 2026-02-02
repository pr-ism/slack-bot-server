package com.slack.bot.application.setting.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.domain.setting.NotificationSettings;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationSettingsUpdaterTest {

    @Test
    void 전략을_기반으로_알림_설정을_변경한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                DeliverySpace.TRIGGER_CHANNEL,
                false,
                false,
                false,
                false
        );
        NotificationSettingsUpdater updater = NotificationSettingsUpdater.create();

        // when
        updater.update(settings, request);

        // then
        assertAll(
                () -> assertThat(settings.getReservationConfirmed().getDeliverySpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL),
                () -> assertThat(settings.getOptionalNotifications().isReservationCanceledConfirmationEnabled()).isFalse(),
                () -> assertThat(settings.getOptionalNotifications().isReviewReminderEnabled()).isFalse(),
                () -> assertThat(settings.getOptionalNotifications().isPullRequestMentionEnabled()).isFalse(),
                () -> assertThat(settings.getOptionalNotifications().isReviewCompletedEnabled()).isFalse()
        );
    }
}
