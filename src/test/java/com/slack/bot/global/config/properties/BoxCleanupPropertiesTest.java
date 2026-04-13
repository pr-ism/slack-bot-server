package com.slack.bot.global.config.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoxCleanupPropertiesTest {

    @Test
    void box_cleanup_properties는_유효한_설정을_생성한다() {
        // when
        BoxCleanupProperties properties = new BoxCleanupProperties(true, 3_600_000L, 45L, 250);

        // then
        assertAll(
                () -> assertThat(properties.enabled()).isTrue(),
                () -> assertThat(properties.fixedDelayMs()).isEqualTo(3_600_000L),
                () -> assertThat(properties.retentionDays()).isEqualTo(45L),
                () -> assertThat(properties.deleteBatchSize()).isEqualTo(250)
        );
    }

    @Test
    void box_cleanup_properties는_fixed_delay가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new BoxCleanupProperties(true, 0L, 30L, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("box.fixedDelayMs는 0보다 커야 합니다.");
    }

    @Test
    void box_cleanup_properties는_retention_days가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new BoxCleanupProperties(true, 1_800_000L, 0L, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("box.retentionDays는 0보다 커야 합니다.");
    }

    @Test
    void box_cleanup_properties는_delete_batch_size가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new BoxCleanupProperties(true, 1_800_000L, 30L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("box.deleteBatchSize는 0보다 커야 합니다.");
    }
}
