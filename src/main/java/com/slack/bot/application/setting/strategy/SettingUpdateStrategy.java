package com.slack.bot.application.setting.strategy;

import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.domain.setting.NotificationSettings;

public interface SettingUpdateStrategy {

    void apply(NotificationSettings settings, UpdateNotificationSettingsRequest request);
}
