package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.async.review-interaction")
public record ReviewInteractionAsyncProperties(
        int corePoolSize,
        int maxPoolSize,
        String threadNamePrefix,
        int queueCapacity
) {
}
