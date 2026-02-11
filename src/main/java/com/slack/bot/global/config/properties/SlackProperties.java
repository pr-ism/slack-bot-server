package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack")
public record SlackProperties(
        String signingSecret,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scopes
) {
}
