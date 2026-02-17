package com.slack.bot.infrastructure.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxTest {

    @Test
    void pending_생성시_기본_상태는_PENDING이고_시도횟수는_0이다() {
        // when
        SlackNotificationOutbox actual = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                                .idempotencyKey("key")
                                                                .teamId("T1")
                                                                .channelId("channel1")
                                                                .userId("user1")
                                                                .text("text")
                                                                .blocksJson(null)
                                                                .fallbackText(null)
                                                                .build();

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT),
                () -> assertThat(actual.getChannelId()).isEqualTo("channel1"),
                () -> assertThat(actual.getUserId()).isEqualTo("user1"),
                () -> assertThat(actual.getText()).isEqualTo("text")
        );
    }

    @Test
    void markProcessing_호출시_PROCESSING_상태로_변경되고_시도횟수가_증가한다() {
        // given
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                                .idempotencyKey("key")
                                                                .teamId("T1")
                                                                .channelId("channel1")
                                                                .userId("user1")
                                                                .text("text")
                                                                .blocksJson("[{}]")
                                                                .fallbackText("fallback")
                                                                .build();

        // when
        Instant processingStartedAt = Instant.parse("2026-02-15T00:00:00Z");

        outbox.markProcessing(processingStartedAt);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PROCESSING),
                () -> assertThat(outbox.getProcessingStartedAt()).isEqualTo(processingStartedAt),
                () -> assertThat(outbox.getProcessingAttempt()).isEqualTo(1)
        );
    }

    @Test
    void markSent_호출시_SENT_상태와_전송시각이_저장된다() {
        // given
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                                .idempotencyKey("key")
                                                                .teamId("T1")
                                                                .channelId("channel1")
                                                                .userId("user1")
                                                                .text("text")
                                                                .blocksJson("[{}]")
                                                                .fallbackText("fallback")
                                                                .build();

        // when
        Instant sentAt = Instant.parse("2026-02-15T01:00:00Z");

        outbox.markSent(sentAt);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT),
                () -> assertThat(outbox.getSentAt()).isEqualTo(sentAt),
                () -> assertThat(outbox.getFailedAt()).isNull(),
                () -> assertThat(outbox.getFailureReason()).isNull()
        );
    }

    @Test
    void markFailed_호출시_FAILED_상태와_실패정보가_저장된다() {
        // given
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                                .idempotencyKey("key")
                                                                .teamId("T1")
                                                                .channelId("channel1")
                                                                .userId("user1")
                                                                .text("text")
                                                                .blocksJson("[{}]")
                                                                .fallbackText("fallback")
                                                                .build();

        // when
        Instant failedAt = Instant.parse("2026-02-15T02:00:00Z");

        outbox.markFailed(failedAt, "failure", SlackInteractivityFailureType.RETRY_EXHAUSTED);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(outbox.getFailureType()).isEqualTo(SlackInteractivityFailureType.RETRY_EXHAUSTED)
        );
    }

    @Test
    void markRetryPending_호출시_RETRY_PENDING_상태와_실패정보가_저장된다() {
        // given
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                                .idempotencyKey("key")
                                                                .teamId("T1")
                                                                .channelId("channel1")
                                                                .userId("user1")
                                                                .text("text")
                                                                .blocksJson("[{}]")
                                                                .fallbackText("fallback")
                                                                .build();

        // when
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        outbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailureReason()).isEqualTo("retry")
        );
    }
}
