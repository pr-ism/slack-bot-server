package com.slack.bot.application.setting;

import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.application.setting.dto.response.NotificationSettingsResponse;
import com.slack.bot.application.setting.strategy.NotificationSettingsUpdater;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.domain.setting.repository.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final NotificationSettingsUpdater notificationSettingsUpdater;
    private final NotificationSettingsRepository notificationSettingsRepository;

    @Transactional
    public NotificationSettingsResponse findSettings(Long projectMemberId) {
        NotificationSettings settings = findOrCreateSettingsEntity(projectMemberId);

        return NotificationSettingsResponse.from(settings);
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(
            Long projectMemberId,
            UpdateNotificationSettingsRequest request
    ) {
        NotificationSettings settings = findOrCreateSettingsEntity(projectMemberId);

        notificationSettingsUpdater.update(settings, request);
        return NotificationSettingsResponse.from(settings);
    }

    private NotificationSettings findOrCreateSettingsEntity(Long projectMemberId) {
        return notificationSettingsRepository.findByProjectMemberId(projectMemberId)
                     .orElseGet(() -> {
                         NotificationSettings notificationSettings = NotificationSettings.defaults(projectMemberId);

                         return notificationSettingsRepository.saveOrFindOnDuplicate(notificationSettings);
                     });
    }
}
