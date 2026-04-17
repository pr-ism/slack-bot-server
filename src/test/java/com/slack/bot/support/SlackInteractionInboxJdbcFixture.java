package com.slack.bot.support;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class SlackInteractionInboxJdbcFixture {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public SlackInteractionInboxJdbcFixture(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<SlackInteractionInboxHistory> historiesOf(Long inboxId) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       inbox_id,
                       processing_attempt,
                       status,
                       completed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox_history
                WHERE inbox_id = :inboxId
                ORDER BY processing_attempt ASC
                """,
                new MapSqlParameterSource().addValue("inboxId", inboxId),
                (resultSet, rowNum) -> mapHistory(resultSet)
        );
    }

    public List<SlackInteractionInbox> findAllInboxes() {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       interaction_type,
                       idempotency_key,
                       payload_json,
                       status,
                       processing_attempt,
                       processing_started_at,
                       processed_at,
                       failed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox
                ORDER BY id ASC
                """,
                (resultSet, rowNum) -> mapInbox(resultSet)
        );
    }

    public List<SlackInteractionInbox> findInboxesByIds(List<Long> inboxIds) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       interaction_type,
                       idempotency_key,
                       payload_json,
                       status,
                       processing_attempt,
                       processing_started_at,
                       processed_at,
                       failed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox
                WHERE id IN (:inboxIds)
                ORDER BY id ASC
                """,
                new MapSqlParameterSource().addValue("inboxIds", inboxIds),
                (resultSet, rowNum) -> mapInbox(resultSet)
        );
    }

    public List<SlackInteractionInboxHistory> findHistoriesByInboxIds(List<Long> inboxIds) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       inbox_id,
                       processing_attempt,
                       status,
                       completed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox_history
                WHERE inbox_id IN (:inboxIds)
                ORDER BY inbox_id ASC, processing_attempt ASC
                """,
                new MapSqlParameterSource().addValue("inboxIds", inboxIds),
                (resultSet, rowNum) -> mapHistory(resultSet)
        );
    }

    public SlackInteractionInbox findInboxByIdempotencyKey(String idempotencyKey) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT id,
                       interaction_type,
                       idempotency_key,
                       payload_json,
                       status,
                       processing_attempt,
                       processing_started_at,
                       processed_at,
                       failed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox
                WHERE idempotency_key = :idempotencyKey
                """,
                new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey),
                (resultSet, rowNum) -> mapInbox(resultSet)
        );
    }

    public SlackInteractionInbox findInboxByPayloadJson(String payloadJson) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT id,
                       interaction_type,
                       idempotency_key,
                       payload_json,
                       status,
                       processing_attempt,
                       processing_started_at,
                       processed_at,
                       failed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox
                WHERE payload_json = :payloadJson
                """,
                new MapSqlParameterSource().addValue("payloadJson", payloadJson),
                (resultSet, rowNum) -> mapInbox(resultSet)
        );
    }

    public int countInboxByIdempotencyKey(String idempotencyKey) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM slack_interaction_inbox
                WHERE idempotency_key = :idempotencyKey
                """,
                new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey),
                Integer.class
        );
    }

    public int countHistoryByInboxId(Long inboxId) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM slack_interaction_inbox_history
                WHERE inbox_id = :inboxId
                """,
                new MapSqlParameterSource().addValue("inboxId", inboxId),
                Integer.class
        );
    }

    public void deleteInboxById(Long inboxId) {
        namedParameterJdbcTemplate.update(
                """
                DELETE FROM slack_interaction_inbox
                WHERE id = :inboxId
                """,
                new MapSqlParameterSource().addValue("inboxId", inboxId)
        );
    }

    private SlackInteractionInbox mapInbox(ResultSet resultSet) throws SQLException {
        return SlackInteractionInbox.rehydrate(
                resultSet.getLong("id"),
                SlackInteractionInboxType.valueOf(resultSet.getString("interaction_type")),
                resultSet.getString("idempotency_key"),
                resultSet.getString("payload_json"),
                SlackInteractionInboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("processing_attempt"),
                toProcessingLease(resultSet),
                toProcessedTime(resultSet),
                toFailedTime(resultSet),
                toFailure(resultSet)
        );
    }

    private SlackInteractionInboxHistory mapHistory(ResultSet resultSet) throws SQLException {
        return SlackInteractionInboxHistory.rehydrate(
                resultSet.getLong("id"),
                resultSet.getLong("inbox_id"),
                resultSet.getInt("processing_attempt"),
                SlackInteractionInboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("completed_at").toInstant(),
                toFailure(resultSet)
        );
    }

    private BoxProcessingLease toProcessingLease(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("processing_started_at") == null) {
            return BoxProcessingLease.idle();
        }

        return BoxProcessingLease.claimed(
                resultSet.getTimestamp("processing_started_at").toInstant()
        );
    }

    private BoxEventTime toProcessedTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("processed_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(
                resultSet.getTimestamp("processed_at").toInstant()
        );
    }

    private BoxEventTime toFailedTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("failed_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(
                resultSet.getTimestamp("failed_at").toInstant()
        );
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
