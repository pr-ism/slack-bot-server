package com.slack.bot.infrastructure.interaction.box.out;

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
class SlackNotificationOutboxHistoryTest {

    @Test
    void completed는_outboxId가_필수다() {
        // when
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutboxHistory.completed(
                null,
                1,
                SlackNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRYABLE)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outboxId는 비어 있을 수 없습니다.");
    }

    @Test
    void FAILED_history는_absent_failure를_허용하지_않는다() {
        // when & then
        assertThatThrownBy(() -> SlackNotificationOutboxHistory.completed(
                10L,
                1,
                SlackNotificationOutboxStatus.FAILED,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("완료 실패 정보는 비어 있을 수 없습니다.");
    }

    @Test
    void RETRY_PENDING_history는_PROCESSING_TIMEOUT_failureType을_허용한다() {
        // when
        SlackNotificationOutboxHistory history = SlackNotificationOutboxHistory.completed(
                10L,
                1,
                SlackNotificationOutboxStatus.RETRY_PENDING,
                Instant.parse("2026-03-27T00:00:00Z"),
                BoxFailureSnapshot.present("timeout", SlackInteractionFailureType.PROCESSING_TIMEOUT)
        );

        // then
        assertAll(
                () -> assertThat(history.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailure().type()).isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT)
        );
    }
}
