package com.slack.bot.infrastructure.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoxDetailValueTypesTest {

    @Test
    void processing_lease_detail은_startedAt을_갱신한다() {
        // given
        BoxProcessingLeaseDetail detail = BoxProcessingLeaseDetail.of(
                BoxDetailOwnerType.SLACK_NOTIFICATION_OUTBOX,
                Instant.parse("2026-03-24T00:00:00Z")
        );

        // when
        detail.updateStartedAt(Instant.parse("2026-03-24T00:01:00Z"));

        // then
        assertAll(
                () -> assertThat(detail.getOwnerType()).isEqualTo(BoxDetailOwnerType.SLACK_NOTIFICATION_OUTBOX),
                () -> assertThat(detail.getStartedAt()).isEqualTo(Instant.parse("2026-03-24T00:01:00Z"))
        );
    }

    @Test
    void event_time_detail은_owner와_이벤트종류를_보관한다() {
        // when
        BoxEventTimeDetail detail = BoxEventTimeDetail.of(
                BoxDetailOwnerType.SLACK_NOTIFICATION_OUTBOX,
                BoxEventKind.SENT,
                Instant.parse("2026-03-24T00:02:00Z")
        );

        // then
        assertAll(
                () -> assertThat(detail.getOwnerType()).isEqualTo(BoxDetailOwnerType.SLACK_NOTIFICATION_OUTBOX),
                () -> assertThat(detail.getEventKind()).isEqualTo(BoxEventKind.SENT),
                () -> assertThat(detail.getOccurredAt()).isEqualTo(Instant.parse("2026-03-24T00:02:00Z"))
        );
    }

    @Test
    void failure_detail은_실패시각과_타입이름을_보관한다() {
        // when
        BoxFailureDetail detail = BoxFailureDetail.of(
                BoxDetailOwnerType.SLACK_NOTIFICATION_OUTBOX,
                Instant.parse("2026-03-24T00:03:00Z"),
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );

        // then
        assertAll(
                () -> assertThat(detail.getOwnerType()).isEqualTo(BoxDetailOwnerType.SLACK_NOTIFICATION_OUTBOX),
                () -> assertThat(detail.getFailedAt()).isEqualTo(Instant.parse("2026-03-24T00:03:00Z")),
                () -> assertThat(detail.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(detail.getFailureTypeName()).isEqualTo("RETRY_EXHAUSTED")
        );
    }

    @Test
    void history_failure_detail은_failureType이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> BoxHistoryFailureDetail.of(
                BoxDetailOwnerType.SLACK_NOTIFICATION_OUTBOX_HISTORY,
                "failure",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 비어 있을 수 없습니다.");
    }
}
