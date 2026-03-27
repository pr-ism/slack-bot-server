package com.slack.bot.infrastructure.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxHistoryTest {

    @Test
    void completed는_outboxId가_null이어도_history를_생성한다() {
        // when
        SlackNotificationOutboxHistory history = SlackNotificationOutboxHistory.completed(
                null,
                1,
                SlackNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                "failure",
                SlackInteractionFailureType.NONE
        );

        // then
        assertAll(
                () -> assertThat(history.getOutboxId()).isNull(),
                () -> assertThat(history.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(history.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING)
        );
    }

    @Test
    void bindOutboxId는_null_outboxId를_실제_id로_바인딩한다() {
        // given
        SlackNotificationOutboxHistory history = SlackNotificationOutboxHistory.completed(
                null,
                1,
                SlackNotificationOutboxStatus.FAILED,
                Instant.parse("2026-03-27T00:00:00Z"),
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // when
        SlackNotificationOutboxHistory actual = history.bindOutboxId(10L);

        // then
        assertAll(
                () -> assertThat(actual.getOutboxId()).isEqualTo(10L),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
    }

    @Test
    void bindOutboxId는_다른_id로_변경할_수_없다() {
        // given
        SlackNotificationOutboxHistory history = SlackNotificationOutboxHistory.completed(
                10L,
                1,
                SlackNotificationOutboxStatus.SENT,
                Instant.parse("2026-03-27T00:00:00Z"),
                FailureSnapshotDefaults.NO_FAILURE_REASON,
                SlackInteractionFailureType.NONE
        );

        // when & then
        assertThatThrownBy(() -> history.bindOutboxId(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("history outboxId를 다른 값으로 변경할 수 없습니다.");
    }
}
