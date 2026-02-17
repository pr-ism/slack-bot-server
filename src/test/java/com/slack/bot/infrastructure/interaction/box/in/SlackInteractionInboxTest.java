package com.slack.bot.infrastructure.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

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
                () -> assertThat(actual.getProcessingAttempt()).isZero()
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
    void markProcessing을_호출하면_상태가_PROCESSING으로_변경되고_시도횟수가_증가한다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-15T00:00:00Z"), ZoneOffset.UTC);

        // when
        inbox.markProcessing(fixedClock.instant());

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSING),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(inbox.getProcessingStartedAt()).isEqualTo(fixedClock.instant()),
                () -> assertThat(inbox.getFailedAt()).isNull(),
                () -> assertThat(inbox.getFailureReason()).isNull(),
                () -> assertThat(inbox.getFailureType()).isNull()
        );
    }

    @Test
    void markProcessing은_RETRY_PENDING에서_재진입하면_이전_실패정보를_초기화한다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        inbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));
        inbox.markRetryPending(Instant.parse("2026-02-15T00:01:00Z"), "retry");

        // when
        inbox.markProcessing(Instant.parse("2026-02-15T00:02:00Z"));

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSING),
                () -> assertThat(inbox.getFailedAt()).isNull(),
                () -> assertThat(inbox.getFailureReason()).isNull(),
                () -> assertThat(inbox.getFailureType()).isNull()
        );
    }

    @Test
    void markProcessed를_호출하면_처리완료_상태와_처리시각이_저장된다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        inbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        // when
        Instant processedAt = Instant.parse("2026-02-15T00:01:00Z");

        inbox.markProcessed(processedAt);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(inbox.getProcessedAt()).isEqualTo(processedAt),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull()
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
        inbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        // when
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");

        inbox.markFailed(failedAt, "failure", SlackInteractivityFailureType.BUSINESS_INVARIANT);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(inbox.getFailureType()).isEqualTo(SlackInteractivityFailureType.BUSINESS_INVARIANT)
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
        inbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        // when
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        inbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("retry"),
                () -> assertThat(inbox.getFailureType()).isNull()
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
        assertThatThrownBy(() -> inbox.markFailed(null, "failure", SlackInteractivityFailureType.BUSINESS_INVARIANT))
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
        assertThatThrownBy(() -> inbox.markFailed(failedAt, null, SlackInteractivityFailureType.BUSINESS_INVARIANT))
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
        assertThatThrownBy(() -> inbox.markFailed(failedAt, " ", SlackInteractivityFailureType.BUSINESS_INVARIANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureType이_null이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        Instant failedAt = Instant.parse("2026-02-15T01:00:00Z");

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(failedAt, "failure", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessing은_processingStartedAt이_null이면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inbox.markProcessing(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingStartedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessing은_PENDING이나_RETRY_PENDING이_아니면_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key",
                "{}"
        );
        inbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));
        inbox.markProcessed(Instant.parse("2026-02-15T00:01:00Z"));

        // when & then
        assertThatThrownBy(() -> inbox.markProcessing(Instant.parse("2026-02-15T00:02:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다. 현재: PROCESSED");
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
        assertThatThrownBy(() -> inbox.markFailed(
                Instant.parse("2026-02-15T01:00:00Z"),
                "failure",
                SlackInteractivityFailureType.BUSINESS_INVARIANT
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FAILED 전이는 PROCESSING 상태에서만 가능합니다. 현재: PENDING");
    }
}
