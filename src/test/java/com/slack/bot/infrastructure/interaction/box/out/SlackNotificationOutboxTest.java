package com.slack.bot.infrastructure.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxTest {

    @Test
    void pending_생성시_기본_상태는_PENDING이고_시도횟수는_0이다() {
        // when
        SlackNotificationOutbox actual = pendingOutbox();

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT),
                () -> assertThat(actual.getChannelId()).isEqualTo("channel1"),
                () -> assertThat(actual.getUserId()).isEqualTo("user1"),
                () -> assertThat(actual.getText()).isEqualTo("text"),
                () -> assertThat(actual.getBlocksJson()).isNull(),
                () -> assertThat(actual.getFallbackText()).isNull(),
                () -> assertThat(actual.getTeamId()).isEqualTo("T1"),
                () -> assertThat(actual.getIdempotencyKey()).isEqualTo("key"),
                () -> assertThat(actual.getProcessingLease().isClaimed()).isFalse(),
                () -> assertThat(actual.getSentTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(actual.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void 메시지_타입이_없으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(null)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox messageType은 비어 있을 수 없습니다.");
    }

    @Test
    void 멱등성_키가_없으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey(" ")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void 멱등성_키가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey(null)
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void 팀_ID가_없으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId(" ")
                                                        .channelId("channel1")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox teamId는 비어 있을 수 없습니다.");
    }

    @Test
    void 팀_ID가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId(null)
                                                        .channelId("channel1")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox teamId는 비어 있을 수 없습니다.");
    }

    @Test
    void 채널_ID가_없으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId(" ")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox channelId는 비어 있을 수 없습니다.");
    }

    @Test
    void 채널_ID가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId(null)
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox channelId는 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_TEXT는_userId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId(null)
                                                        .text("text")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EPHEMERAL 메시지는 userId가 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_TEXT는_userId가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId(" ")
                                                        .text("text")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EPHEMERAL 메시지는 userId가 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_BLOCKS는_userId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId(null)
                                                        .blocksJson("[{}]")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EPHEMERAL 메시지는 userId가 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_BLOCKS는_userId가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId(" ")
                                                        .blocksJson("[{}]")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EPHEMERAL 메시지는 userId가 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_BLOCKS는_blocksJson이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId("user1")
                                                        .blocksJson(null)
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BLOCKS 타입 메시지는 blocksJson이 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_BLOCKS는_blocksJson이_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId("user1")
                                                        .blocksJson(" ")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BLOCKS 타입 메시지는 blocksJson이 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_TEXT는_text가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId("user1")
                                                        .text(null)
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TEXT 타입 메시지는 text가 비어 있을 수 없습니다.");
    }

    @Test
    void EPHEMERAL_TEXT는_text가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .userId("user1")
                                                        .text(" ")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TEXT 타입 메시지는 text가 비어 있을 수 없습니다.");
    }

    @Test
    void CHANNEL_TEXT는_text가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .text(null)
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TEXT 타입 메시지는 text가 비어 있을 수 없습니다.");
    }

    @Test
    void CHANNEL_TEXT는_text가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .text(" ")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TEXT 타입 메시지는 text가 비어 있을 수 없습니다.");
    }

    @Test
    void CHANNEL_BLOCKS는_blocksJson이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .blocksJson(null)
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BLOCKS 타입 메시지는 blocksJson이 비어 있을 수 없습니다.");
    }

    @Test
    void CHANNEL_BLOCKS는_blocksJson이_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutbox.builder()
                                                        .messageType(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS)
                                                        .idempotencyKey("key")
                                                        .teamId("T1")
                                                        .channelId("channel1")
                                                        .blocksJson(" ")
                                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BLOCKS 타입 메시지는 blocksJson이 비어 있을 수 없습니다.");
    }

    @Test
    void markSent_호출시_SENT_상태와_전송시각이_저장된다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when
        Instant sentAt = Instant.parse("2026-02-15T01:00:00Z");
        SlackNotificationOutboxHistory history = outbox.markSent(sentAt);

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT),
                () -> assertThat(outbox.getProcessingLease().isClaimed()).isFalse(),
                () -> assertThat(outbox.getSentTime().occurredAt()).isEqualTo(sentAt),
                () -> assertThat(outbox.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(outbox.getFailure().isPresent()).isFalse(),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getOutboxId()).isEqualTo(10L),
                () -> assertThat(history.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT),
                () -> assertThat(history.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void markSent는_sentAt이_null이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> outbox.markSent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sentAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed_호출시_FAILED_상태와_실패정보가_저장된다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-15T02:00:00Z");
        SlackNotificationOutboxHistory history = outbox.markFailed(
                failedAt,
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(outbox.getProcessingLease().isClaimed()).isFalse(),
                () -> assertThat(outbox.getSentTime().isPresent()).isFalse(),
                () -> assertThat(outbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(outbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getOutboxId()).isEqualTo(10L),
                () -> assertThat(history.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
    }

    @Test
    void markRetryPending_호출시_RETRY_PENDING_상태와_실패정보가_저장된다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");
        SlackNotificationOutboxHistory history = outbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(outbox.getProcessingLease().isClaimed()).isFalse(),
                () -> assertThat(outbox.getSentTime().isPresent()).isFalse(),
                () -> assertThat(outbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailure().reason()).isEqualTo("retry"),
                () -> assertThat(outbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getOutboxId()).isEqualTo(10L),
                () -> assertThat(history.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE)
        );
    }

    @Test
    void markRetryPending은_failureReason이_null이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(failedAt, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failedAt이_null이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(null, "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_공백이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(failedAt, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_null이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T02:00:00Z");

        // when & then
        assertThatThrownBy(() -> outbox.markFailed(
                failedAt,
                null,
                SlackInteractionFailureType.RETRY_EXHAUSTED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failedAt이_null이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> outbox.markFailed(
                null,
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_공백이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T02:00:00Z");

        // when & then
        assertThatThrownBy(() -> outbox.markFailed(
                failedAt,
                " ",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureType이_ABSENT이면_예외를_던진다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        Instant failedAt = Instant.parse("2026-02-15T02:00:00Z");

        // when & then
        assertThatThrownBy(() -> outbox.markFailed(
                failedAt,
                "failure",
                SlackInteractionFailureType.ABSENT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 ABSENT일 수 없습니다.");
    }

    @Test
    void PENDING_상태에서_SENT_전이는_불가능하다() {
        // given
        SlackNotificationOutbox pending = pendingOutbox();

        // when & then
        assertThatThrownBy(() -> pending.markSent(Instant.parse("2026-02-15T01:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENT 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void PENDING_상태에서_RETRY_PENDING_전이는_불가능하다() {
        // given
        SlackNotificationOutbox pending = pendingOutbox();

        // when & then
        assertThatThrownBy(() -> pending.markRetryPending(Instant.parse("2026-02-15T02:00:00Z"), "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETRY_PENDING 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void PENDING_상태에서_FAILED_전이는_불가능하다() {
        // given
        SlackNotificationOutbox pending = pendingOutbox();

        // when & then
        assertThatThrownBy(() -> pending.markFailed(
                Instant.parse("2026-02-15T03:00:00Z"),
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void FAILED_상태에서_SENT_전이는_불가능하다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        outbox.markFailed(
                Instant.parse("2026-02-15T01:00:00Z"),
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // when & then
        assertThatThrownBy(() -> outbox.markSent(Instant.parse("2026-02-15T02:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENT 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void FAILED_상태에서_RETRY_PENDING_전이는_불가능하다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        setProcessingState(outbox, Instant.parse("2026-02-15T00:00:00Z"), 1);
        outbox.markFailed(
                Instant.parse("2026-02-15T01:00:00Z"),
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // when & then
        assertThatThrownBy(() -> outbox.markRetryPending(Instant.parse("2026-02-15T02:00:00Z"), "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETRY_PENDING 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    private SlackNotificationOutbox pendingOutbox() {
        return SlackNotificationOutbox.builder()
                                      .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                      .idempotencyKey("key")
                                      .teamId("T1")
                                      .channelId("channel1")
                                      .userId("user1")
                                      .text("text")
                                      .build();
    }

    private void setProcessingState(
            SlackNotificationOutbox outbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        ReflectionTestUtils.setField(outbox, "identity", SlackNotificationOutboxId.assigned(10L));
        outbox.claim(processingStartedAt);
        ReflectionTestUtils.setField(outbox, "processingAttempt", processingAttempt);
    }
}
