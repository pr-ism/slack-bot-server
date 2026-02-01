package com.slack.bot.domain.setting.repository;

import com.slack.bot.domain.setting.NotificationSettings;
import java.util.Optional;

public interface NotificationSettingsRepository {

    Optional<NotificationSettings> findByProjectMemberId(Long projectMemberId);

    Optional<NotificationSettings> findBySlackUser(String teamId, String slackUserId);

    NotificationSettings save(NotificationSettings notificationSettings);

    NotificationSettings saveOrFindOnDuplicate(NotificationSettings notificationSettings);
}
