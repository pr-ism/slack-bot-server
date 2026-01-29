package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack.event.messages")
public record EventMessageProperties(String welcome) {
}
