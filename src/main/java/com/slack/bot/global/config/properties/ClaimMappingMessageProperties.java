package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack.interactivity.claim-mapping.messages")
public record ClaimMappingMessageProperties(String success) {
}
