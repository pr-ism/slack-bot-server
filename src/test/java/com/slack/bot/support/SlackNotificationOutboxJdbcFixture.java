package com.slack.bot.support;

import com.slack.bot.infrastructure.common.BoxEventTimeState;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxFieldState;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.persistence.out.SlackNotificationOutboxHistoryRow;
import com.slack.bot.infrastructure.interaction.box.persistence.out.SlackNotificationOutboxRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class SlackNotificationOutboxJdbcFixture {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public SlackNotificationOutboxJdbcFixture(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<SlackNotificationOutbox> findOutboxesByIds(List<Long> outboxIds) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       message_type,
                       idempotency_key,
                       team_id,
                       channel_id,
                       user_id_state,
                       user_id,
                       text_state,
                       text,
                       blocks_json_state,
                       blocks_json,
                       fallback_text_state,
                       fallback_text,
                       status,
                       processing_attempt,
                       processing_lease_state,
                       processing_started_at,
                       sent_time_state,
                       sent_at,
                       failed_time_state,
                       failed_at,
                       failure_state,
                       failure_reason,
                       failure_type
                FROM slack_notification_outbox
                WHERE id IN (:outboxIds)
                ORDER BY id ASC
                """,
                new MapSqlParameterSource().addValue("outboxIds", outboxIds),
                (resultSet, rowNum) -> mapOutbox(resultSet)
        );
    }

    public List<SlackNotificationOutboxHistory> historiesOf(Long outboxId) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       outbox_id,
                       processing_attempt,
                       status,
                       completed_at,
                       failure_state,
                       failure_reason,
                       failure_type
                FROM slack_notification_outbox_history
                WHERE outbox_id = :outboxId
                ORDER BY processing_attempt ASC
                """,
                new MapSqlParameterSource().addValue("outboxId", outboxId),
                (resultSet, rowNum) -> mapHistory(resultSet)
        );
    }

    public List<SlackNotificationOutboxHistory> findHistoriesByOutboxIds(List<Long> outboxIds) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       outbox_id,
                       processing_attempt,
                       status,
                       completed_at,
                       failure_state,
                       failure_reason,
                       failure_type
                FROM slack_notification_outbox_history
                WHERE outbox_id IN (:outboxIds)
                ORDER BY outbox_id ASC, processing_attempt ASC
                """,
                new MapSqlParameterSource().addValue("outboxIds", outboxIds),
                (resultSet, rowNum) -> mapHistory(resultSet)
        );
    }

    private SlackNotificationOutbox mapOutbox(ResultSet resultSet) throws SQLException {
        String failureReason = resultSet.getString("failure_reason");
        String failureType = resultSet.getString("failure_type");
        SlackInteractionFailureType actualFailureType = null;
        if (failureReason == null && failureType == null) {
            actualFailureType = null;
        } else if (failureReason == null || failureType == null) {
            throw new IllegalStateException("failure_reason과 failure_type은 항상 함께 세팅되어야 합니다.");
        } else {
            actualFailureType = SlackInteractionFailureType.valueOf(failureType);
        }

        Timestamp processingStartedAt = resultSet.getTimestamp("processing_started_at");
        Timestamp sentAt = resultSet.getTimestamp("sent_at");
        Timestamp failedAt = resultSet.getTimestamp("failed_at");
        Instant actualProcessingStartedAt = null;
        if (processingStartedAt != null) {
            actualProcessingStartedAt = processingStartedAt.toInstant();
        }
        Instant actualSentAt = null;
        if (sentAt != null) {
            actualSentAt = sentAt.toInstant();
        }
        Instant actualFailedAt = null;
        if (failedAt != null) {
            actualFailedAt = failedAt.toInstant();
        }
        SlackNotificationOutboxRow row = SlackNotificationOutboxRow.builder()
                                                                   .id(resultSet.getLong("id"))
                                                                   .messageType(
                                                                           SlackNotificationOutboxMessageType.valueOf(
                                                                                   resultSet.getString("message_type")
                                                                           )
                                                                   )
                                                                   .idempotencyKey(resultSet.getString("idempotency_key"))
                                                                   .teamId(resultSet.getString("team_id"))
                                                                   .channelId(resultSet.getString("channel_id"))
                                                                   .userIdState(
                                                                           SlackNotificationOutboxFieldState.valueOf(
                                                                                   resultSet.getString("user_id_state")
                                                                           )
                                                                   )
                                                                   .userId(resultSet.getString("user_id"))
                                                                   .textState(
                                                                           SlackNotificationOutboxFieldState.valueOf(
                                                                                   resultSet.getString("text_state")
                                                                           )
                                                                   )
                                                                   .text(resultSet.getString("text"))
                                                                   .blocksJsonState(
                                                                           SlackNotificationOutboxFieldState.valueOf(
                                                                                   resultSet.getString("blocks_json_state")
                                                                           )
                                                                   )
                                                                   .blocksJson(resultSet.getString("blocks_json"))
                                                                   .fallbackTextState(
                                                                           SlackNotificationOutboxFieldState.valueOf(
                                                                                   resultSet.getString("fallback_text_state")
                                                                           )
                                                                   )
                                                                   .fallbackText(resultSet.getString("fallback_text"))
                                                                   .status(SlackNotificationOutboxStatus.valueOf(resultSet.getString("status")))
                                                                   .processingAttempt(resultSet.getInt("processing_attempt"))
                                                                   .processingLeaseState(
                                                                           BoxProcessingLeaseState.valueOf(
                                                                                   resultSet.getString("processing_lease_state")
                                                                           )
                                                                   )
                                                                   .processingStartedAt(actualProcessingStartedAt)
                                                                   .sentTimeState(
                                                                           BoxEventTimeState.valueOf(
                                                                                   resultSet.getString("sent_time_state")
                                                                           )
                                                                   )
                                                                   .sentAt(actualSentAt)
                                                                   .failedTimeState(
                                                                           BoxEventTimeState.valueOf(
                                                                                   resultSet.getString("failed_time_state")
                                                                           )
                                                                   )
                                                                   .failedAt(actualFailedAt)
                                                                   .failureState(
                                                                           BoxFailureState.valueOf(
                                                                                   resultSet.getString("failure_state")
                                                                           )
                                                                   )
                                                                   .failureReason(failureReason)
                                                                   .failureType(actualFailureType)
                                                                   .build();

        return row.toDomain();
    }

    private SlackNotificationOutboxHistory mapHistory(ResultSet resultSet) throws SQLException {
        String failureReason = resultSet.getString("failure_reason");
        String failureType = resultSet.getString("failure_type");
        SlackInteractionFailureType actualFailureType = null;
        if (failureReason == null && failureType == null) {
            actualFailureType = null;
        } else if (failureReason == null || failureType == null) {
            throw new IllegalStateException("failure_reason과 failure_type은 항상 함께 세팅되어야 합니다.");
        } else {
            actualFailureType = SlackInteractionFailureType.valueOf(failureType);
        }

        SlackNotificationOutboxHistoryRow row = SlackNotificationOutboxHistoryRow.builder()
                                                                                 .id(resultSet.getLong("id"))
                                                                                 .outboxId(resultSet.getLong("outbox_id"))
                                                                                 .processingAttempt(resultSet.getInt("processing_attempt"))
                                                                                 .status(
                                                                                         SlackNotificationOutboxStatus.valueOf(
                                                                                                 resultSet.getString("status")
                                                                                         )
                                                                                 )
                                                                                 .completedAt(resultSet.getTimestamp("completed_at").toInstant())
                                                                                 .failureState(
                                                                                         BoxFailureState.valueOf(
                                                                                                 resultSet.getString("failure_state")
                                                                                         )
                                                                                 )
                                                                                 .failureReason(failureReason)
                                                                                 .failureType(actualFailureType)
                                                                                 .build();

        return row.toDomain();
    }
}
