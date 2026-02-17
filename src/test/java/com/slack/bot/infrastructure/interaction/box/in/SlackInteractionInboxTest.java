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
                () -> assertThat(inbox.getProcessingStartedAt()).isEqualTo(fixedClock.instant())
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

        // when
        Instant processedAt = Instant.parse("2026-02-15T00:00:00Z");

        inbox.markProcessed(processedAt);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(inbox.getProcessedAt()).isEqualTo(processedAt)
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

        // when
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        inbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("retry")
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
}
