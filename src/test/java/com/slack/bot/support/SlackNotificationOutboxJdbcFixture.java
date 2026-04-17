package com.slack.bot.support;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxFieldState;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStringField;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                       processing_started_at,
                       sent_at,
                       failed_at,
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
        return SlackNotificationOutbox.rehydrate(
                resultSet.getLong("id"),
                SlackNotificationOutboxMessageType.valueOf(resultSet.getString("message_type")),
                resultSet.getString("idempotency_key"),
                resultSet.getString("team_id"),
                resultSet.getString("channel_id"),
                toStringField(
                        SlackNotificationOutboxFieldState.valueOf(resultSet.getString("user_id_state")),
                        resultSet.getString("user_id")
                ),
                toStringField(
                        SlackNotificationOutboxFieldState.valueOf(resultSet.getString("text_state")),
                        resultSet.getString("text")
                ),
                toStringField(
                        SlackNotificationOutboxFieldState.valueOf(resultSet.getString("blocks_json_state")),
                        resultSet.getString("blocks_json")
                ),
                toStringField(
                        SlackNotificationOutboxFieldState.valueOf(resultSet.getString("fallback_text_state")),
                        resultSet.getString("fallback_text")
                ),
                SlackNotificationOutboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("processing_attempt"),
                toProcessingLease(resultSet),
                toSentTime(resultSet),
                toFailedTime(resultSet),
                toFailure(resultSet)
        );
    }

    private SlackNotificationOutboxHistory mapHistory(ResultSet resultSet) throws SQLException {
        return SlackNotificationOutboxHistory.rehydrate(
                resultSet.getLong("id"),
                resultSet.getLong("outbox_id"),
                resultSet.getInt("processing_attempt"),
                SlackNotificationOutboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("completed_at").toInstant(),
                toFailure(resultSet)
        );
    }

    private SlackNotificationOutboxStringField toStringField(
            SlackNotificationOutboxFieldState state,
            String value
    ) {
        if (state == SlackNotificationOutboxFieldState.ABSENT) {
            return SlackNotificationOutboxStringField.absent();
        }

        return SlackNotificationOutboxStringField.present(value);
    }

    private BoxProcessingLease toProcessingLease(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("processing_started_at") == null) {
            return BoxProcessingLease.idle();
        }

        return BoxProcessingLease.claimed(resultSet.getTimestamp("processing_started_at").toInstant());
    }

    private BoxEventTime toSentTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("sent_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(resultSet.getTimestamp("sent_at").toInstant());
    }

    private BoxEventTime toFailedTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("failed_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(resultSet.getTimestamp("failed_at").toInstant());
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure(ResultSet resultSet) throws SQLException {
        String failureReason = resultSet.getString("failure_reason");
        String failureType = resultSet.getString("failure_type");

        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }
        if (failureReason == null || failureType == null) {
            throw new IllegalStateException("failure_reason과 failure_type은 항상 함께 세팅되어야 합니다.");
        }

        return BoxFailureSnapshot.present(
                failureReason,
                SlackInteractionFailureType.valueOf(failureType)
        );
    }
}
