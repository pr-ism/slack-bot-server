package com.slack.bot.infrastructure.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        SlackNotificationOutbox actual = pendingOutbox();

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT),
                () -> assertThat(actual.getChannelId()).isEqualTo("channel1"),
                () -> assertThat(actual.getUserId()).isEqualTo("user1"),
                () -> assertThat(actual.getText()).isEqualTo("text"),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[{}]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo("fallback"),
                () -> assertThat(actual.getTeamId()).isEqualTo("T1"),
                () -> assertThat(actual.getIdempotencyKey()).isEqualTo("key")
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
    void markProcessing_호출시_PROCESSING_상태로_변경되고_시도횟수가_증가한다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();

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
        SlackNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

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
        SlackNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

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
        SlackNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));

        // when
        Instant failedAt = Instant.parse("2026-02-15T03:00:00Z");

        outbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(outbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(outbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(outbox.getFailureReason()).isEqualTo("retry")
        );
    }

    @Test
    void markProcessing은_RETRY_PENDING에서_재진입하면_이전_실패정보를_초기화한다() {
        // given
        SlackNotificationOutbox outbox = pendingOutbox();
        outbox.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));
        outbox.markRetryPending(Instant.parse("2026-02-15T00:01:00Z"), "retry");

        // when
        outbox.markProcessing(Instant.parse("2026-02-15T00:02:00Z"));

        // then
        assertAll(
                () -> assertThat(outbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PROCESSING),
                () -> assertThat(outbox.getFailedAt()).isNull(),
                () -> assertThat(outbox.getFailureReason()).isNull(),
                () -> assertThat(outbox.getFailureType()).isNull()
        );
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
                SlackInteractivityFailureType.RETRY_EXHAUSTED
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void SENT_상태에서_PROCESSING_전이는_불가능하다() {
        // given
        SlackNotificationOutbox sent = pendingOutbox();
        sent.markProcessing(Instant.parse("2026-02-15T00:00:00Z"));
        sent.markSent(Instant.parse("2026-02-15T01:00:00Z"));

        // when & then
        assertThatThrownBy(() -> sent.markProcessing(Instant.parse("2026-02-15T04:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다.");
    }

    private SlackNotificationOutbox pendingOutbox() {
        return SlackNotificationOutbox.builder()
                                      .messageType(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT)
                                      .idempotencyKey("key")
                                      .teamId("T1")
                                      .channelId("channel1")
                                      .userId("user1")
                                      .text("text")
                                      .blocksJson("[{}]")
                                      .fallbackText("fallback")
                                      .build();
    }
}
