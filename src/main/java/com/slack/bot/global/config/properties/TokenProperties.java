package com.slack.bot.global.config.properties;

import com.slack.bot.domain.auth.TokenType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("token")
public record TokenProperties(
        String accessKey,
        String refreshKey,
        String encryptionKey,
        String issuer,
        int accessExpiredSeconds,
        int refreshExpiredSeconds,
        long accessExpiredMillisSeconds,
        long refreshExpiredMillisSeconds
) {

    public String findTokenKey(TokenType tokenType) {
        if (tokenType.isAccessToken()) {
            return accessKey;
        }

        return refreshKey;
    }

    public Long findExpiredMillisSeconds(TokenType tokenType) {
        if (tokenType.isAccessToken()) {
            return accessExpiredMillisSeconds;
        }

        return refreshExpiredMillisSeconds;
    }
}
