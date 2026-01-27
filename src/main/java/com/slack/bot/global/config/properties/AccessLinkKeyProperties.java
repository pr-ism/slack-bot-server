package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("access-link")
public record AccessLinkKeyProperties(String keySecret) {
}
