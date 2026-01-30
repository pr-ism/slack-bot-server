package com.slack.bot.presentation.setting;

import com.slack.bot.application.setting.NotificationSettingsService;
import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.application.setting.dto.response.NotificationSettingsResponse;
import com.slack.bot.global.resolver.dto.ProjectMemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping
    public ResponseEntity<NotificationSettingsResponse> findNotificationSettings(ProjectMemberId projectMemberId) {
        NotificationSettingsResponse response = notificationSettingsService.findSettings(projectMemberId.value());

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<NotificationSettingsResponse> updateNotificationSettings(
            ProjectMemberId projectMemberId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request
    ) {
        NotificationSettingsResponse response = notificationSettingsService.updateSettings(projectMemberId.value(), request);

        return ResponseEntity.ok(response);
    }
}
