package com.slack.bot.global.config.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractionWorkerPropertiesTest {

    @Test
    void block_actions_properties는_유효한_polling_설정을_생성한다() {
        // when
        InteractionWorkerProperties.BlockActionsProperties properties =
                new InteractionWorkerProperties.BlockActionsProperties(1_000L, 60_000L, 30_000L, 123);

        // then
        assertAll(
                () -> assertThat(properties.pollCapMs()).isEqualTo(30_000L),
                () -> assertThat(properties.timeoutRecoveryBatchSize()).isEqualTo(123)
        );
    }

    @Test
    void block_actions_properties는_poll_delay가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.BlockActionsProperties(0L, 60_000L, 30_000L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blockActions.pollDelayMs는 0보다 커야 합니다.");
    }

    @Test
    void block_actions_properties는_processing_timeout이_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.BlockActionsProperties(1_000L, 0L, 30_000L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blockActions.processingTimeoutMs는 0보다 커야 합니다.");
    }

    @Test
    void block_actions_properties는_poll_cap이_poll_delay보다_작으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.BlockActionsProperties(1_000L, 60_000L, 999L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blockActions.pollCapMs는 pollDelayMs보다 크거나 같아야 합니다.");
    }

    @Test
    void block_actions_properties는_timeout_recovery_batch_size가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.BlockActionsProperties(1_000L, 60_000L, 30_000L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blockActions.timeoutRecoveryBatchSize는 0보다 커야 합니다.");
    }

    @Test
    void view_submission_properties는_유효한_polling_설정을_생성한다() {
        // when
        InteractionWorkerProperties.ViewSubmissionProperties properties =
                new InteractionWorkerProperties.ViewSubmissionProperties(1_000L, 60_000L, 30_000L, 77);

        // then
        assertAll(
                () -> assertThat(properties.pollCapMs()).isEqualTo(30_000L),
                () -> assertThat(properties.timeoutRecoveryBatchSize()).isEqualTo(77)
        );
    }

    @Test
    void view_submission_properties는_poll_cap이_poll_delay보다_작으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.ViewSubmissionProperties(1_000L, 60_000L, 999L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("viewSubmission.pollCapMs는 pollDelayMs보다 크거나 같아야 합니다.");
    }

    @Test
    void view_submission_properties는_timeout_recovery_batch_size가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.ViewSubmissionProperties(1_000L, 60_000L, 30_000L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("viewSubmission.timeoutRecoveryBatchSize는 0보다 커야 합니다.");
    }

    @Test
    void outbox_properties는_유효한_polling_설정을_생성한다() {
        // when
        InteractionWorkerProperties.OutboxProperties properties =
                new InteractionWorkerProperties.OutboxProperties(1_000L, 60_000L, 30_000L, 50);

        // then
        assertAll(
                () -> assertThat(properties.pollCapMs()).isEqualTo(30_000L),
                () -> assertThat(properties.timeoutRecoveryBatchSize()).isEqualTo(50)
        );
    }

    @Test
    void outbox_properties는_processing_timeout이_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.OutboxProperties(1_000L, 0L, 30_000L, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox.processingTimeoutMs는 0보다 커야 합니다.");
    }

    @Test
    void outbox_properties는_timeout_recovery_batch_size가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> new InteractionWorkerProperties.OutboxProperties(1_000L, 60_000L, 30_000L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox.timeoutRecoveryBatchSize는 0보다 커야 합니다.");
    }
}
