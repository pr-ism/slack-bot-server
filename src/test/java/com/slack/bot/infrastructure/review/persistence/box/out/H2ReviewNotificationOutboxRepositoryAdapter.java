package com.slack.bot.infrastructure.review.persistence.box.out;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class H2ReviewNotificationOutboxRepositoryAdapter extends ReviewNotificationOutboxRepositoryAdapter {

    public H2ReviewNotificationOutboxRepositoryAdapter(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaReviewNotificationOutboxRepository repository,
            JpaReviewNotificationOutboxHistoryRepository historyRepository
    ) {
        super(namedParameterJdbcTemplate, repository, historyRepository);
    }

    @Override
    protected String buildEnqueueSql() {
        return """
                MERGE INTO review_notification_outbox AS target
                USING (
                    VALUES (
                        :messageType,
                        :idempotencyKey,
                        :projectId,
                        :teamId,
                        :channelId,
                        :payloadJsonState,
                        :payloadJson,
                        :blocksJsonState,
                        :blocksJson,
                        :attachmentsJsonState,
                        :attachmentsJson,
                        :fallbackTextState,
                        :fallbackText,
                        :pendingStatus,
                        :processingAttempt,
                        :processingStartedAt,
                        :sentAt,
                        :failedAt,
                        :failureReason,
                        :failureType
                    )
                ) AS source (
                    message_type,
                    idempotency_key,
                    project_id,
                    team_id,
                    channel_id,
                    payload_json_state,
                    payload_json,
                    blocks_json_state,
                    blocks_json,
                    attachments_json_state,
                    attachments_json,
                    fallback_text_state,
                    fallback_text,
                    status,
                    processing_attempt,
                    processing_started_at,
                    sent_at,
                    failed_at,
                    failure_reason,
                    failure_type
                )
                ON target.idempotency_key = source.idempotency_key
                WHEN NOT MATCHED THEN
                    INSERT (
                        created_at,
                        updated_at,
                        message_type,
                        idempotency_key,
                        project_id,
                        team_id,
                        channel_id,
                        payload_json_state,
                        payload_json,
                        blocks_json_state,
                        blocks_json,
                        attachments_json_state,
                        attachments_json,
                        fallback_text_state,
                        fallback_text,
                        status,
                        processing_attempt,
                        processing_started_at,
                        sent_at,
                        failed_at,
                        failure_reason,
                        failure_type
                    )
                    VALUES (
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        source.message_type,
                        source.idempotency_key,
                        source.project_id,
                        source.team_id,
                        source.channel_id,
                        source.payload_json_state,
                        source.payload_json,
                        source.blocks_json_state,
                        source.blocks_json,
                        source.attachments_json_state,
                        source.attachments_json,
                        source.fallback_text_state,
                        source.fallback_text,
                        source.status,
                        source.processing_attempt,
                        source.processing_started_at,
                        source.sent_at,
                        source.failed_at,
                        source.failure_reason,
                        source.failure_type
                    )
                """;
    }
}
