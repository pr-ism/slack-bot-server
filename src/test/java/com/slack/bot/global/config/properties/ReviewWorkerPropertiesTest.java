package com.slack.bot.global.config.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewWorkerPropertiesTest {

    @Test
    void inbox_properties는_유효한_polling_설정을_생성한다() {
        // when
        ReviewWorkerProperties.InboxProperties properties =
                new ReviewWorkerProperties.InboxProperties(1_000L, 30_000L, 30, 60_000L, 25);

        // then
        assertAll(
                () -> assertThat(properties.pollCapMs()).isEqualTo(30_000L),
                () -> assertThat(properties.timeoutRecoveryBatchSize()).isEqualTo(25)
        );
    }

    @Test
    void inbox_properties는_poll_delay가_0이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new ReviewWorkerProperties.InboxProperties(0L, 30_000L, 30, 60_000L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inbox.pollDelayMs는 0보다 커야 합니다.");
    }

    @Test
    void inbox_properties는_poll_cap이_음수면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new ReviewWorkerProperties.InboxProperties(1_000L, -1L, 30, 60_000L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inbox.pollCapMs는 0보다 커야 합니다.");
    }

    @Test
    void inbox_properties는_poll_cap이_poll_delay보다_작으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new ReviewWorkerProperties.InboxProperties(1_000L, 999L, 30, 60_000L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inbox.pollCapMs는 pollDelayMs보다 크거나 같아야 합니다.");
    }

    @Test
    void inbox_properties는_timeout_recovery_batch_size가_0이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new ReviewWorkerProperties.InboxProperties(1_000L, 30_000L, 30, 60_000L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inbox.timeoutRecoveryBatchSize는 0보다 커야 합니다.");
    }

    @Test
    void outbox_properties는_유효한_polling_설정을_생성한다() {
        // when
        ReviewWorkerProperties.OutboxProperties properties =
                new ReviewWorkerProperties.OutboxProperties(1_000L, 30_000L, 50, 60_000L);

        // then
        assertThat(properties.pollCapMs()).isEqualTo(30_000L);
    }

    @Test
    void outbox_properties는_poll_cap이_poll_delay보다_작으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new ReviewWorkerProperties.OutboxProperties(1_000L, 999L, 50, 60_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox.pollCapMs는 pollDelayMs보다 크거나 같아야 합니다.");
    }
}
