package com.slack.bot.infrastructure.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
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
                () -> assertThat(actual.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(actual.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailure().isPresent()).isFalse()
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
    void rehydrate는_PENDING과_0이_아닌_processingAttempt_조합을_허용하지_않는다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.rehydrate(
                1L,
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}",
                SlackInteractionInboxStatus.PENDING,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PENDING 상태의 processingAttempt는 0이어야 합니다.");
    }

    @Test
    void rehydrate는_PROCESSING과_idle_processingLease_조합을_허용하지_않는다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.rehydrate(
                1L,
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}",
                SlackInteractionInboxStatus.PROCESSING,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PROCESSING 상태는 processingLease를 보유해야 합니다.");
    }

    @Test
    void rehydrate는_PROCESSED와_absent_processedTime_조합을_허용하지_않는다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.rehydrate(
                1L,
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}",
                SlackInteractionInboxStatus.PROCESSED,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PROCESSED 상태는 processedTime이 있어야 합니다.");
    }

    @Test
    void rehydrate는_RETRY_PENDING과_absent_failedTime_조합을_허용하지_않는다() {
        // given
        BoxFailureSnapshot<SlackInteractionFailureType> failure = BoxFailureSnapshot.present(
                "retryable",
                SlackInteractionFailureType.RETRYABLE
        );

        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.rehydrate(
                1L,
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}",
                SlackInteractionInboxStatus.RETRY_PENDING,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                failure
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RETRY_PENDING 상태는 failedTime이 있어야 합니다.");
    }

    @Test
    void rehydrate는_FAILED와_absent_failure_조합을_허용하지_않는다() {
        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.rehydrate(
                1L,
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}",
                SlackInteractionInboxStatus.FAILED,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.present(Instant.parse("2026-02-15T01:00:00Z")),
                BoxFailureSnapshot.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FAILED 상태는 failure가 있어야 합니다.");
    }

    @Test
    void rehydrate는_FAILED와_RETRYABLE_failureType_조합을_허용하지_않는다() {
        // given
        BoxFailureSnapshot<SlackInteractionFailureType> failure = BoxFailureSnapshot.present(
                "retryable",
                SlackInteractionFailureType.RETRYABLE
        );

        // when & then
        assertThatThrownBy(() -> SlackInteractionInbox.rehydrate(
                1L,
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}",
                SlackInteractionInboxStatus.FAILED,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.present(Instant.parse("2026-02-15T01:00:00Z")),
                failure
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FAILED 상태의 failureType이 올바르지 않습니다.");
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
                () -> assertThat(inbox.getProcessedTime().occurredAt()).isEqualTo(processedAt),
                () -> assertThat(inbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(inbox.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(inbox.getFailure().isPresent()).isFalse(),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(history.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void hasClaimedProcessingLease는_현재_lease_보유_여부와_시작시각_일치_여부를_제공한다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant processingStartedAt = Instant.parse("2026-02-15T00:00:00Z");
        setProcessingState(inbox, processingStartedAt, 1);

        // when & then
        assertAll(
                () -> assertThat(inbox.hasClaimedProcessingLease()).isTrue(),
                () -> assertThat(inbox.hasClaimedProcessingLease(processingStartedAt)).isTrue(),
                () -> assertThat(inbox.hasClaimedProcessingLease(processingStartedAt.plusSeconds(1))).isFalse(),
                () -> assertThat(inbox.currentProcessingLeaseStartedAt()).isEqualTo(processingStartedAt)
        );
    }

    @Test
    void currentProcessingLeaseStartedAt는_lease가_없으면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inbox.currentProcessingLeaseStartedAt())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("processingLease를 보유하고 있지 않습니다.");
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
                () -> assertThat(inbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(inbox.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(inbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
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
                () -> assertThat(inbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(inbox.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(inbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailure().reason()).isEqualTo("retry"),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE)
        );
    }

    @Test
    void markFailure는_재시도_가능하고_최대시도_미만이면_RETRY_PENDING으로_마킹한다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        // when
        SlackInteractionInboxHistory history = inbox.markFailure(failedAt, "retry", true, 2);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING)
        );
    }

    @Test
    void markFailure는_재시도_가능하고_최대시도에_도달하면_FAILED로_마킹한다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 2);
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        // when
        SlackInteractionInboxHistory history = inbox.markFailure(failedAt, "retry exhausted", true, 2);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED)
        );
    }

    @Test
    void markFailure는_재시도_불가이면_즉시_FAILED로_마킹한다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        // when
        SlackInteractionInboxHistory history = inbox.markFailure(failedAt, "business invariant", false, 2);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED)
        );
    }

    @Test
    void markFailure는_maxAttempts가_0이하면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> inbox.markFailure(
                Instant.parse("2026-02-15T03:00:00Z"),
                "retry",
                true,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempts는 0보다 커야 합니다.");
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
    void markFailed는_failureType이_ABSENT이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(failedAt, "failure", SlackInteractionFailureType.ABSENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 ABSENT일 수 없습니다.");
    }

    @Test
    void markFailed는_RETRYABLE_failureType을_허용하지_않는다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(failedAt, "failure", SlackInteractionFailureType.RETRYABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FAILED failureType이 올바르지 않습니다.");
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
        inbox.claim(processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
    }
}
