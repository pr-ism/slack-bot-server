package com.slack.bot.infrastructure.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxTest {

    @Test
    void builder로_생성하면_기본_상태는_PENDING이고_시도횟수는_0이다() {
        // when
        ReviewNotificationOutbox outbox = pendingOutbox();

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.PENDING),
                () -> assertThat(outbox.getProcessingAttempt()).isZero(),
                () -> assertThat(outbox.getIdempotencyKey()).isEqualTo("idempotency"),
                () -> assertThat(outbox.getTeamId()).isEqualTo("T1"),
                () -> assertThat(outbox.getChannelId()).isEqualTo("C1"),
                () -> assertThat(outbox.getBlocksJson()).isEqualTo("[{\"type\":\"section\"}]"),
                () -> assertThat(outbox.getFallbackText()).isEqualTo("fallback")
        );
    }

    @Test
    void idempotencyKey가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey(null)
                                                         .teamId("T1")
                                                         .channelId("C1")
                                                         .blocksJson("[{}]")
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void idempotencyKey가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey(" ")
                                                         .teamId("T1")
                                                         .channelId("C1")
                                                         .blocksJson("[{}]")
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void teamId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey("idempotency")
                                                         .teamId(null)
                                                         .channelId("C1")
                                                         .blocksJson("[{}]")
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamId는 비어 있을 수 없습니다.");
    }

    @Test
    void teamId가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey("idempotency")
                                                         .teamId(" ")
                                                         .channelId("C1")
                                                         .blocksJson("[{}]")
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamId는 비어 있을 수 없습니다.");
    }

    @Test
    void channelId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey("idempotency")
                                                         .teamId("T1")
                                                         .channelId(null)
                                                         .blocksJson("[{}]")
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channelId는 비어 있을 수 없습니다.");
    }

    @Test
    void channelId가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey("idempotency")
                                                         .teamId("T1")
                                                         .channelId(" ")
                                                         .blocksJson("[{}]")
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channelId는 비어 있을 수 없습니다.");
    }

    @Test
    void blocksJson이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey("idempotency")
                                                         .teamId("T1")
                                                         .channelId("C1")
                                                         .blocksJson(null)
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blocksJson은 비어 있을 수 없습니다.");
    }

    @Test
    void blocksJson이_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.builder()
                                                         .idempotencyKey("idempotency")
                                                         .teamId("T1")
                                                         .channelId("C1")
                                                         .blocksJson(" ")
                                                         .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blocksJson은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessing을_호출하면_PROCESSING_상태로_변경되고_시도횟수가_증가한다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();

        // when
        Instant processingStartedAt = Instant.parse("2026-02-24T00:00:00Z");
        outbox.markProcessing(processingStartedAt);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.PROCESSING),
                () -> assertThat(outbox.getProcessingStartedAt()).isEqualTo(processingStartedAt),
                () -> assertThat(outbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(outbox.getFailedAt()).isNull(),
                () -> assertThat(outbox.getFailureReason()).isNull(),
                () -> assertThat(outbox.getFailureType()).isNull()
        );
    }

    @Test
    void markProcessing은_RETRY_PENDING에서_재진입하면_이전_실패정보를_초기화한다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));
        outbox.markRetryPending(Instant.parse("2026-02-24T00:01:00Z"), "retry");

        // when
        outbox.markProcessing(Instant.parse("2026-02-24T00:02:00Z"));

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.PROCESSING),
                () -> assertThat(outbox.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(outbox.getFailedAt()).isNull(),
                () -> assertThat(outbox.getFailureReason()).isNull(),
                () -> assertThat(outbox.getFailureType()).isNull()
        );
    }

    @Test
    void markProcessing은_processingStartedAt이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();

        // when & then
        assertThatThrownBy(() -> outbox.markProcessing(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingStartedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessing은_PENDING이나_RETRY_PENDING이_아니면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));
        outbox.markSent(Instant.parse("2026-02-24T00:01:00Z"));

        // when & then
        assertThatThrownBy(() -> outbox.markProcessing(Instant.parse("2026-02-24T00:02:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다.");
    }

    @Test
    void markSent를_호출하면_SENT_상태와_전송시각이_저장된다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when
        Instant sentAt = Instant.parse("2026-02-24T00:03:00Z");
        outbox.markSent(sentAt);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.SENT),
                () -> assertThat(outbox.getSentAt()).isEqualTo(sentAt),
                () -> assertThat(outbox.getFailedAt()).isNull(),
                () -> assertThat(outbox.getFailureReason()).isNull(),
                () -> assertThat(outbox.getFailureType()).isNull()
        );
    }

    @Test
    void markSent는_sentAt이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(() -> outbox.markSent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sentAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markSent는_PROCESSING_상태가_아니면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();

        // when & then
        assertThatThrownBy(() -> outbox.markSent(Instant.parse("2026-02-24T00:03:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENT 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void markRetryPending을_호출하면_RETRY_PENDING_상태와_실패정보가_저장된다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:04:00Z");
        outbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(outbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailureReason()).isEqualTo("retry"),
                () -> assertThat(outbox.getFailureType()).isNull()
        );
    }

    @Test
    void markRetryPending은_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(null, "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(Instant.parse("2026-02-24T00:04:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_공백이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(Instant.parse("2026-02-24T00:04:00Z"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_PROCESSING_상태가_아니면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(Instant.parse("2026-02-24T00:04:00Z"), "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETRY_PENDING 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void markFailed를_호출하면_FAILED_상태와_실패정보가_저장된다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:05:00Z");
        outbox.markFailed(failedAt, "failure", SlackInteractionFailureType.RETRY_EXHAUSTED);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.FAILED),
                () -> assertThat(outbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(outbox.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
    }

    @Test
    void markFailed는_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(() -> outbox.markFailed(null, "failure", SlackInteractionFailureType.RETRY_EXHAUSTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(
                () -> outbox.markFailed(
                        Instant.parse("2026-02-24T00:05:00Z"),
                        null,
                        SlackInteractionFailureType.RETRY_EXHAUSTED
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_공백이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(
                () -> outbox.markFailed(
                        Instant.parse("2026-02-24T00:05:00Z"),
                        " ",
                        SlackInteractionFailureType.RETRY_EXHAUSTED
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureType이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-24T00:00:00Z"));

        // when & then
        assertThatThrownBy(
                () -> outbox.markFailed(
                        Instant.parse("2026-02-24T00:05:00Z"),
                        "failure",
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_PROCESSING_상태가_아니면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();

        // when & then
        assertThatThrownBy(
                () -> outbox.markFailed(
                        Instant.parse("2026-02-24T00:05:00Z"),
                        "failure",
                        SlackInteractionFailureType.RETRY_EXHAUSTED
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    private ReviewNotificationOutbox pendingOutbox() {
        return ReviewNotificationOutbox.builder()
                                       .idempotencyKey("idempotency")
                                       .teamId("T1")
                                       .channelId("C1")
                                       .blocksJson("[{\"type\":\"section\"}]")
                                       .fallbackText("fallback")
                                       .build();
    }
}
