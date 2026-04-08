package com.slack.bot.infrastructure.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxHistoryTest {

    @Test
    void completed는_inboxId가_null이어도_history를_생성한다() {
        // when
        SlackInteractionInboxHistory history = SlackInteractionInboxHistory.completed(
                null,
                1,
                SlackInteractionInboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRYABLE)
        );

        // then
        assertAll(
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRYABLE)
        );
    }

    @Test
    void bindInboxId는_null_inboxId를_실제_id로_바인딩한다() {
        // given
        SlackInteractionInboxHistory history = SlackInteractionInboxHistory.completed(
                null,
                1,
                SlackInteractionInboxStatus.FAILED,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.BUSINESS_INVARIANT)
        );

        // when
        SlackInteractionInboxHistory actual = history.bindInboxId(10L);

        // then
        assertAll(
                () -> assertThat(actual.getInboxId()).isEqualTo(10L),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
        );
    }

    @Test
    void bindInboxId는_다른_id로_변경할_수_없다() {
        // given
        SlackInteractionInboxHistory history = SlackInteractionInboxHistory.completed(
                10L,
                1,
                SlackInteractionInboxStatus.PROCESSED,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.absent()
        );

        // when & then
        assertThatThrownBy(() -> history.bindInboxId(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("history inboxId를 다른 값으로 변경할 수 없습니다.");
    }

    @Test
    void RETRY_PENDING_history는_PROCESSING_TIMEOUT_failureType을_허용한다() {
        // when
        SlackInteractionInboxHistory history = SlackInteractionInboxHistory.completed(
                10L,
                1,
                SlackInteractionInboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("timeout", SlackInteractionFailureType.PROCESSING_TIMEOUT)
        );

        // then
        assertAll(
                () -> assertThat(history.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT)
        );
    }

    @Test
    void completed는_null_failure을_허용하지_않는다() {
        // given
        Instant completedAt = Instant.parse("2026-03-27T00:00:00Z");

        // when & then
        assertThatThrownBy(() -> SlackInteractionInboxHistory.completed(
                10L,
                1,
                SlackInteractionInboxStatus.FAILED,
                completedAt,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failure는 비어 있을 수 없습니다.");
    }

    @Test
    void FAILED_history는_RETRYABLE_failureType을_허용하지_않는다() {
        // given
        Instant completedAt = Instant.parse("2026-03-27T00:00:00Z");
        BoxFailureSnapshot<SlackInteractionFailureType> failure = BoxFailureSnapshot.present(
                "retryable",
                SlackInteractionFailureType.RETRYABLE
        );

        // when & then
        assertThatThrownBy(() -> SlackInteractionInboxHistory.completed(
                10L,
                1,
                SlackInteractionInboxStatus.FAILED,
                completedAt,
                failure
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FAILED history의 failureType이 올바르지 않습니다.");
    }

    @Test
    void FAILED_history는_PROCESSING_TIMEOUT_failureType을_허용하지_않는다() {
        // given
        Instant completedAt = Instant.parse("2026-03-27T00:00:00Z");
        BoxFailureSnapshot<SlackInteractionFailureType> failure = BoxFailureSnapshot.present(
                "processing-timeout",
                SlackInteractionFailureType.PROCESSING_TIMEOUT
        );

        // when & then
        assertThatThrownBy(() -> SlackInteractionInboxHistory.completed(
                10L,
                1,
                SlackInteractionInboxStatus.FAILED,
                completedAt,
                failure
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FAILED history의 failureType이 올바르지 않습니다.");
    }

    @Test
    void validateHistoryFailure는_완료되지_않은_inbox_status를_거부한다() {
        // given
        BoxFailureSnapshot<SlackInteractionFailureType> failure = BoxFailureSnapshot.present(
                "failure",
                SlackInteractionFailureType.RETRYABLE
        );

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> SlackInteractionInboxStatus.PENDING.validateHistoryFailure(failure))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("history status는 완료된 상태여야 합니다."),
                () -> assertThatThrownBy(() -> SlackInteractionInboxStatus.PROCESSING.validateHistoryFailure(failure))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("history status는 완료된 상태여야 합니다.")
        );
    }
}
