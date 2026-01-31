package com.slack.bot.application.setting.strategy;

import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.domain.setting.NotificationSettings;

class PrMentionUpdateStrategy implements SettingUpdateStrategy {

    @Override
    public void apply(NotificationSettings settings, UpdateNotificationSettingsRequest request) {
        settings.updatePrMention(request.prMentionEnabled());
    }
}
