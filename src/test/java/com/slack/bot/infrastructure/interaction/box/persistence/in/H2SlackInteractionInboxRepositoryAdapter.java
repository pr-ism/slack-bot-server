package com.slack.bot.infrastructure.interaction.box.persistence.in;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class H2SlackInteractionInboxRepositoryAdapter extends SlackInteractionInboxRepositoryAdapter {

    public H2SlackInteractionInboxRepositoryAdapter(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaSlackInteractionInboxRepository repository,
            JpaSlackInteractionInboxHistoryRepository historyRepository
    ) {
        super(namedParameterJdbcTemplate, repository, historyRepository);
    }

    @Override
    protected String buildEnqueueSql() {
        return """
                MERGE INTO slack_interaction_inbox AS target
                USING (
                    VALUES (
                        :interactionType,
                        :idempotencyKey,
                        :payloadJson,
                        :pendingStatus,
                        :processingAttempt,
                        :noProcessingStartedAt,
                        :noProcessedAt,
                        :noFailureAt,
                        :noFailureReason,
                        :noneFailureType
                    )
                ) AS source (
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
                )
                ON target.idempotency_key = source.idempotency_key
                WHEN NOT MATCHED THEN
                    INSERT (
                        created_at,
                        updated_at,
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
                    )
                    VALUES (
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        source.interaction_type,
                        source.idempotency_key,
                        source.payload_json,
                        source.status,
                        source.processing_attempt,
                        source.processing_started_at,
                        source.processed_at,
                        source.failed_at,
                        source.failure_reason,
                        source.failure_type
                    )
                """;
    }
}
