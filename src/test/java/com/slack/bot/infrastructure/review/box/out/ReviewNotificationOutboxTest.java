package com.slack.bot.infrastructure.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxTest {

    @Test
    void channel_blocks로_생성하면_기본_상태는_PENDING이고_시도횟수는_0이다() {
        // when
        ReviewNotificationOutbox outbox = pendingOutbox();

        // then
        assertAll(
                () -> assertThat(outbox.getMessageType()).isEqualTo(ReviewNotificationOutboxMessageType.CHANNEL_BLOCKS),
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.PENDING),
                () -> assertThat(outbox.getProcessingAttempt()).isZero(),
                () -> assertThat(outbox.getIdempotencyKey()).isEqualTo("idempotency"),
                () -> assertThat(outbox.getTeamId()).isEqualTo("T1"),
                () -> assertThat(outbox.getChannelId()).isEqualTo("C1"),
                () -> assertThat(outbox.getBlocksJson().value()).isEqualTo("[{\"type\":\"section\"}]"),
                () -> assertThat(outbox.getFallbackText().value()).isEqualTo("fallback"),
                () -> assertThat(outbox.getAttachmentsJson().isPresent()).isFalse()
        );
    }

    @Test
    void semantic_payload만으로도_outbox를_생성할_수_있다() {
        // when
        ReviewNotificationOutbox outbox = ReviewNotificationOutbox.semantic(
                "idempotency",
                1L,
                "T1",
                "C1",
                "{\"repositoryName\":\"repo\"}"
        );

        // then
        assertAll(
                () -> assertThat(outbox.getMessageType()).isEqualTo(ReviewNotificationOutboxMessageType.SEMANTIC),
                () -> assertThat(outbox.requiredProjectId()).isEqualTo(1L),
                () -> assertThat(outbox.requiredPayloadJson()).isEqualTo("{\"repositoryName\":\"repo\"}"),
                () -> assertThat(outbox.getBlocksJson().isPresent()).isFalse(),
                () -> assertThat(outbox.hasSemanticPayload()).isTrue()
        );
    }

    @Test
    void semantic_outbox는_projectId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.semantic("idempotency", null, "T1", "C1", "{\"k\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId는 1 이상의 값이어야 합니다.");
    }

    @Test
    void idempotencyKey가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.channelBlocks(
                null,
                "T1",
                "C1",
                "[{}]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void idempotencyKey가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.channelBlocks(
                " ",
                "T1",
                "C1",
                "[{}]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void teamId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.channelBlocks(
                "idempotency",
                null,
                "C1",
                "[{}]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamId는 비어 있을 수 없습니다.");
    }

    @Test
    void teamId가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.channelBlocks(
                "idempotency",
                " ",
                "C1",
                "[{}]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamId는 비어 있을 수 없습니다.");
    }

    @Test
    void channelId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.channelBlocks(
                "idempotency",
                "T1",
                null,
                "[{}]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channelId는 비어 있을 수 없습니다.");
    }

    @Test
    void channelId가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.channelBlocks(
                "idempotency",
                "T1",
                " ",
                "[{}]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channelId는 비어 있을 수 없습니다.");
    }

    @Test
    void blocksJson이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> ReviewNotificationOutbox.channelBlocks(
                "idempotency",
                "T1",
                "C1",
                null,
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("필드 값은 비어 있을 수 없습니다.");
    }

    @Test
    void markSent를_호출하면_SENT_상태와_전송시각이_저장된다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

        // when
        Instant sentAt = Instant.parse("2026-02-24T00:03:00Z");
        ReviewNotificationOutboxHistory history = outbox.markSent(sentAt);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.SENT),
                () -> assertThat(outbox.getProcessingStartedAt()).isEqualTo(
                        FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                ),
                () -> assertThat(outbox.getSentAt()).isEqualTo(sentAt),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_AT),
                () -> assertThat(outbox.getFailureReason()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_REASON),
                () -> assertThat(outbox.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getOutboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.SENT),
                () -> assertThat(history.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE)
        );
    }

    @Test
    void markSent는_sentAt이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

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
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:04:00Z");
        ReviewNotificationOutboxHistory history = outbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(outbox.getProcessingStartedAt()).isEqualTo(
                        FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                ),
                () -> assertThat(outbox.getSentAt()).isEqualTo(FailureSnapshotDefaults.NO_SENT_AT),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailureReason()).isEqualTo("retry"),
                () -> assertThat(outbox.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getOutboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE)
        );
    }

    @Test
    void markRetryPending은_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(null, "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(Instant.parse("2026-02-24T00:04:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_공백이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

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
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:05:00Z");
        ReviewNotificationOutboxHistory history = outbox.markFailed(
                failedAt,
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.FAILED),
                () -> assertThat(outbox.getProcessingStartedAt()).isEqualTo(
                        FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                ),
                () -> assertThat(outbox.getSentAt()).isEqualTo(FailureSnapshotDefaults.NO_SENT_AT),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(outbox.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getOutboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewNotificationOutboxStatus.FAILED)
        );
    }

    @Test
    void markFailed는_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> outbox.markFailed(null, "failure", SlackInteractionFailureType.RETRY_EXHAUSTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

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
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

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
    void markFailed는_failureType이_NONE이면_예외를_던진다() {
        // given
        ReviewNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-24T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(
                () -> outbox.markFailed(
                        Instant.parse("2026-02-24T00:05:00Z"),
                        "failure",
                        SlackInteractionFailureType.NONE
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 NONE일 수 없습니다.");
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
        return ReviewNotificationOutbox.channelBlocks(
                "idempotency",
                "T1",
                "C1",
                "[{\"type\":\"section\"}]",
                ReviewNotificationOutboxStringField.absent(),
                ReviewNotificationOutboxStringField.present("fallback")
        );
    }

    private void setProcessingState(
            ReviewNotificationOutbox outbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        ReflectionTestUtils.setField(outbox, "status", ReviewNotificationOutboxStatus.PROCESSING);
        ReflectionTestUtils.setField(outbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(outbox, "sentAt", FailureSnapshotDefaults.NO_SENT_AT);
        ReflectionTestUtils.setField(outbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(outbox, "failedAt", FailureSnapshotDefaults.NO_FAILURE_AT);
        ReflectionTestUtils.setField(outbox, "failureReason", FailureSnapshotDefaults.NO_FAILURE_REASON);
        ReflectionTestUtils.setField(outbox, "failureType", SlackInteractionFailureType.NONE);
    }
}
