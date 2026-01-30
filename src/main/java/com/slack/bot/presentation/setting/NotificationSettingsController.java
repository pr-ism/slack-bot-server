package com.slack.bot.presentation.setting;

import com.slack.bot.application.setting.NotificationSettingsService;
import com.slack.bot.application.setting.dto.request.UpdateNotificationSettingsRequest;
import com.slack.bot.application.setting.dto.response.NotificationSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping
    public ResponseEntity<NotificationSettingsResponse> findNotificationSettings(
            @RequestParam Long projectMemberId
    ) {
        NotificationSettingsResponse response = notificationSettingsService.findSettings(projectMemberId);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<NotificationSettingsResponse> updateNotificationSettings(
            @RequestParam Long projectMemberId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request
    ) {
        NotificationSettingsResponse response = notificationSettingsService.updateSettings(projectMemberId, request);

        return ResponseEntity.ok(response);
    }
}
