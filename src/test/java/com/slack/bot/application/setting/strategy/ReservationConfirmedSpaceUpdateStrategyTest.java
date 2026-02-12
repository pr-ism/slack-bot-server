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
class ReservationConfirmedSpaceUpdateStrategyTest {

    @Test
    void 전달_공간_설정을_변경한다() {
        // given
        NotificationSettings settings = NotificationSettings.defaults(1L);
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                DeliverySpace.TRIGGER_CHANNEL,
                true,
                true,
                true,
                true,
                true
        );
        ReservationConfirmedSpaceUpdateStrategy strategy = new ReservationConfirmedSpaceUpdateStrategy();

        // when
        strategy.apply(settings, request);

        // then
        assertThat(settings.getReservationConfirmed().getDeliverySpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL);
    }
}
