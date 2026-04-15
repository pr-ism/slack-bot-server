package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.cleanup.box")
public record BoxCleanupProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("1800000") long fixedDelayMs,
        @DefaultValue("30") long retentionDays,
        @DefaultValue("500") int deleteBatchSize
) {

    public BoxCleanupProperties() {
        this(false, 1_800_000L, 30L, 500);
    }

    public BoxCleanupProperties {
        if (fixedDelayMs <= 0L) {
            throw new IllegalArgumentException("box.fixedDelayMs는 0보다 커야 합니다.");
        }
        if (retentionDays <= 0L) {
            throw new IllegalArgumentException("box.retentionDays는 0보다 커야 합니다.");
        }
        if (deleteBatchSize <= 0) {
            throw new IllegalArgumentException("box.deleteBatchSize는 0보다 커야 합니다.");
        }
    }
}
