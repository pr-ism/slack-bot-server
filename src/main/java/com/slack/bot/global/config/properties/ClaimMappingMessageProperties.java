package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack.interaction.claim-mapping.messages")
public record ClaimMappingMessageProperties(String success) {

    private static final String DEFAULT_SUCCESS_MESSAGE_TEMPLATE = "✅ 등록되었습니다! (Github: %s)";

    public ClaimMappingMessageProperties {
        if (success == null || success.isBlank()) {
            success = DEFAULT_SUCCESS_MESSAGE_TEMPLATE;
        }
    }
}
