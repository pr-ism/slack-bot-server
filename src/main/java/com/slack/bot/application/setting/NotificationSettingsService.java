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
    public NotificationSettingsResponse getOrCreateSettings(Long projectMemberId) {
        NotificationSettings settings = notificationSettingsRepository.findByProjectMemberId(projectMemberId)
                                                     .orElseGet(() -> createDefaultSettings(projectMemberId));

        return NotificationSettingsResponse.from(settings);
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(
            Long projectMemberId,
            UpdateNotificationSettingsRequest request
    ) {
        NotificationSettings settings = getOrCreateSettingsEntity(projectMemberId);

        notificationSettingsUpdater.update(settings, request);
        return NotificationSettingsResponse.from(settings);
    }

    private NotificationSettings createDefaultSettings(Long projectMemberId) {
        NotificationSettings settings = NotificationSettings.defaults(projectMemberId);

        return notificationSettingsRepository.save(settings);
    }

    private NotificationSettings getOrCreateSettingsEntity(Long projectMemberId) {
        return notificationSettingsRepository.findByProjectMemberId(projectMemberId)
                     .orElseGet(() -> {
                         NotificationSettings notificationSettings = NotificationSettings.defaults(projectMemberId);

                         return notificationSettingsRepository.saveOrGetOnDuplicate(notificationSettings);
                     });
    }
}
