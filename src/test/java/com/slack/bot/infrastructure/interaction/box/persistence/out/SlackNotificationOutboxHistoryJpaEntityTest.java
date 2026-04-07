package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxHistoryJpaEntityTest {

    @Test
    void apply는_PRESENT_failure_후_ABSENT_failure를_재적용하면_센티널로_초기화한다() {
        // given
        SlackNotificationOutboxHistoryJpaEntity entity = new SlackNotificationOutboxHistoryJpaEntity();
        SlackNotificationOutboxHistory failedHistory = SlackNotificationOutboxHistory.completed(
                10L,
                1,
                SlackNotificationOutboxStatus.FAILED,
                Instant.parse("2026-04-07T00:00:00Z"),
                BoxFailureSnapshot.present("failure", SlackInteractionFailureType.RETRY_EXHAUSTED)
        );
        SlackNotificationOutboxHistory sentHistory = SlackNotificationOutboxHistory.completed(
                10L,
                1,
                SlackNotificationOutboxStatus.SENT,
                Instant.parse("2026-04-07T00:01:00Z"),
                BoxFailureSnapshot.absent()
        );
        ReflectionTestUtils.setField(entity, "id", 1L);
        entity.apply(failedHistory);

        // when
        entity.apply(sentHistory);

        // then
        assertAll(
                () -> assertThat(ReflectionTestUtils.getField(entity, "failureState")).isEqualTo(BoxFailureState.ABSENT),
                () -> assertThat(ReflectionTestUtils.getField(entity, "failureReason")).isEqualTo(
                        FailureSnapshotDefaults.NO_FAILURE_REASON
                ),
                () -> assertThat(ReflectionTestUtils.getField(entity, "failureType")).isEqualTo(
                        SlackInteractionFailureType.NONE
                ),
                () -> assertThat(entity.toDomain().getFailure().isPresent()).isFalse()
        );
    }
}
