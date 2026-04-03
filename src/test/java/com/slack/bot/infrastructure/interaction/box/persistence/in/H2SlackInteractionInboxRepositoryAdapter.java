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
                        :idleLeaseState,
                        :absentTimeState,
                        :absentTimeState,
                        :absentFailureState
                    )
                ) AS source (
                    interaction_type,
                    idempotency_key,
                    payload_json,
                    status,
                    processing_attempt,
                    processing_lease_state,
                    processed_time_state,
                    failed_time_state,
                    failure_state
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
                        processing_lease_state,
                        processed_time_state,
                        failed_time_state,
                        failure_state
                    )
                    VALUES (
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        source.interaction_type,
                        source.idempotency_key,
                        source.payload_json,
                        source.status,
                        source.processing_attempt,
                        source.processing_lease_state,
                        source.processed_time_state,
                        source.failed_time_state,
                        source.failure_state
                    )
                """;
    }
}
