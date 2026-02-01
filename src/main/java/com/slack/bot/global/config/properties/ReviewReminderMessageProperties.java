package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack.reminder.messages")
public record ReviewReminderMessageProperties(String reviewee, String reviewer) {
}
