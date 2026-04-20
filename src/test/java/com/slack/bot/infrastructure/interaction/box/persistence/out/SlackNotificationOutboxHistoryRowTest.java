package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxHistoryRowTest {

    @Test
    void from은_ABSENT_failure를_row와_domain에_그대로_반영한다() {
        // given
        SlackNotificationOutboxHistory sentHistory = SlackNotificationOutboxHistory.rehydrate(
                1L,
                10L,
                1,
                SlackNotificationOutboxStatus.SENT,
                Instant.parse("2026-04-07T00:01:00Z"),
                BoxFailureSnapshot.absent()
        );

        // when
        SlackNotificationOutboxHistoryRow row = SlackNotificationOutboxHistoryRow.from(sentHistory);
        SlackNotificationOutboxHistory restoredHistory = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getId()).isEqualTo(sentHistory.getId()),
                () -> assertThat(row.getOutboxId()).isEqualTo(sentHistory.getOutboxId()),
                () -> assertThat(row.getProcessingAttempt()).isEqualTo(sentHistory.getProcessingAttempt()),
                () -> assertThat(row.getStatus()).isEqualTo(sentHistory.getStatus()),
                () -> assertThat(row.getCompletedAt()).isEqualTo(sentHistory.getCompletedAt()),
                () -> assertThat(row.getFailureState()).isEqualTo(BoxFailureState.ABSENT),
                () -> assertThat(row.getFailureReason()).isNull(),
                () -> assertThat(row.getFailureType()).isNull(),
                () -> assertThat(restoredHistory.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void from은_PRESENT_failure를_row와_domain에_그대로_반영한다() {
        // given
        SlackNotificationOutboxHistory failedHistory = SlackNotificationOutboxHistory.rehydrate(
                1L,
                10L,
                1,
                SlackNotificationOutboxStatus.FAILED,
                Instant.parse("2026-04-07T00:00:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRY_EXHAUSTED)
        );

        // when
        SlackNotificationOutboxHistoryRow row = SlackNotificationOutboxHistoryRow.from(failedHistory);
        SlackNotificationOutboxHistory restoredHistory = row.toDomain();

        // then
        assertAll(
                () -> assertThat(row.getId()).isEqualTo(failedHistory.getId()),
                () -> assertThat(row.getOutboxId()).isEqualTo(failedHistory.getOutboxId()),
                () -> assertThat(row.getProcessingAttempt()).isEqualTo(failedHistory.getProcessingAttempt()),
                () -> assertThat(row.getStatus()).isEqualTo(failedHistory.getStatus()),
                () -> assertThat(row.getCompletedAt()).isEqualTo(failedHistory.getCompletedAt()),
                () -> assertThat(row.getFailureState()).isEqualTo(BoxFailureState.PRESENT),
                () -> assertThat(row.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(row.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(restoredHistory.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(restoredHistory.getFailure().type())
                        .isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
    }
}
