package com.slack.bot.infrastructure.setting;

import com.slack.bot.domain.setting.NotificationSettings;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface JpaNotificationSettings extends CrudRepository<NotificationSettings, Long> {

    Optional<NotificationSettings> findByProjectMemberId(Long projectMemberId);
}
