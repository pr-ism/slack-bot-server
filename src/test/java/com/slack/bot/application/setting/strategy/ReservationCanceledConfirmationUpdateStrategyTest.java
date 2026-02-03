package com.slack.bot.application.setting.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.domain.setting.NotificationSettings;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationCanceledConfirmationUpdateStrategyTest {

    @Test
    void 리뷰_예약_취소_확인_알림을_변경한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                DeliverySpace.DIRECT_MESSAGE,
                false,
                true,
                true,
                true
        );
        ReservationCanceledConfirmationUpdateStrategy strategy = new ReservationCanceledConfirmationUpdateStrategy();

        // when
        strategy.apply(settings, request);

        // then
        assertThat(settings.getOptionalNotifications().isReservationCanceledConfirmationEnabled()).isFalse();
    }
}
