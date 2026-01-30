package com.slack.bot.application.setting.strategy;

import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.domain.setting.NotificationSettings;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationSettingsUpdater {

    private final List<SettingUpdateStrategy> strategies;

    public static NotificationSettingsUpdater create() {
        List<SettingUpdateStrategy> strategies = buildStrategies();

        return new NotificationSettingsUpdater(strategies);
    }

    private static List<SettingUpdateStrategy> buildStrategies() {
        return List.of(
                new ReservationConfirmedSpaceUpdateStrategy(),
                new ReservationCanceledConfirmationUpdateStrategy(),
                new ReviewReminderUpdateStrategy(),
                new PrMentionUpdateStrategy(),
                new ReviewCompletedUpdateStrategy()
        );
    }

    public NotificationSettingsUpdater(List<SettingUpdateStrategy> strategies) {
        this.strategies = strategies;
    }

    public void update(NotificationSettings settings, UpdateNotificationSettingsRequest request) {
        strategies.forEach(strategy -> strategy.apply(settings, request));
    }
}
