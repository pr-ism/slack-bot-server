package com.slack.bot.infrastructure.interaction.box.in;

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
class SlackInteractionInboxTest {

    @Test
    void pending으로_생성하면_기본값은_PENDING이고_시도횟수는_0이다() {
        // when
        SlackInteractionInbox actual = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{\"type\":\"block_actions\"}"
        );

        // then
        assertAll(
                () -> assertThat(actual.getInteractionType()).isEqualTo(SlackInteractionInboxType.BLOCK_ACTIONS),
                () -> assertThat(actual.getIdempotencyKey()).isEqualTo("key"),
                () -> assertThat(actual.getPayloadJson()).isEqualTo("{\"type\":\"block_actions\"}"),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE)
        );
    }

    @Test
    void pending은_interactionType이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.pending(null, "key", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("interactionType은 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_idempotencyKey가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.pending(SlackInteractionInboxType.BLOCK_ACTIONS, null, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_idempotencyKey가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.pending(SlackInteractionInboxType.BLOCK_ACTIONS, " ", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_payloadJson이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.pending(SlackInteractionInboxType.BLOCK_ACTIONS, "key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payloadJson은 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_payloadJson이_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.pending(SlackInteractionInboxType.BLOCK_ACTIONS, "key", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payloadJson은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessed를_호출하면_처리완료_상태와_처리시각이_저장된다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when
        Instant processedAt = Instant.parse("2026-02-15T00:01:00Z");
        SlackInteractionInboxHistory history = inbox.markProcessed(processedAt);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(inbox.getProcessedAt()).isEqualTo(processedAt),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(inbox.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(history.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE)
        );
    }

    @Test
    void markFailed를_호출하면_실패_상태와_실패정보가_저장된다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");
        SlackInteractionInboxHistory history = inbox.markFailed(
                failedAt,
                "failure",
                SlackInteractionFailureType.BUSINESS_INVARIANT
        );

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(inbox.getFailureType()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED)
        );
    }

    @Test
    void markRetryPending을_호출하면_재시도_대기_상태와_실패정보가_저장된다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");
        SlackInteractionInboxHistory history = inbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("retry"),
                () -> assertThat(inbox.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailureType()).isEqualTo(SlackInteractionFailureType.NONE)
        );
    }

    @Test
    void markProcessed는_processedAt이_null이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inbox.markProcessed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failedAt이_null이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(null, "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_null이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(failedAt, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_공백이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(failedAt, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failedAt이_null이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(null, "failure", SlackInteractionFailureType.BUSINESS_INVARIANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_null이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(failedAt, null, SlackInteractionFailureType.BUSINESS_INVARIANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_공백이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(failedAt, " ", SlackInteractionFailureType.BUSINESS_INVARIANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureType이_NONE이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(failedAt, "failure", SlackInteractionFailureType.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 NONE일 수 없습니다.");
    }

    @Test
    void markProcessed는_PROCESSING이_아니면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inbox.markProcessed(Instant.parse("2026-02-15T00:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROCESSED 전이는 PROCESSING 상태에서만 가능합니다. 현재: PENDING");
    }

    @Test
    void markRetryPending은_PROCESSING이_아니면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(Instant.parse("2026-02-15T03:00:00Z"), "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RETRY_PENDING 전이는 PROCESSING 상태에서만 가능합니다. 현재: PENDING");
    }

    @Test
    void markFailed는_PROCESSING이_아니면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() ->
                inbox.markFailed(
                        Instant.parse("2026-02-15T01:00:00Z"),
                        "failure",
                        SlackInteractionFailureType.BUSINESS_INVARIANT
                )
        ).isInstanceOf(IllegalStateException.class)
         .hasMessage("FAILED 전이는 PROCESSING 상태에서만 가능합니다. 현재: PENDING");
    }

    private void setProcessingState(
            SlackInteractionInbox inbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        ReflectionTestUtils.setField(inbox, "status", SlackInteractionInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(inbox, "failedAt", FailureSnapshotDefaults.NO_FAILURE_AT);
        ReflectionTestUtils.setField(inbox, "failureReason", FailureSnapshotDefaults.NO_FAILURE_REASON);
        ReflectionTestUtils.setField(inbox, "failureType", SlackInteractionFailureType.NONE);
    }
}
