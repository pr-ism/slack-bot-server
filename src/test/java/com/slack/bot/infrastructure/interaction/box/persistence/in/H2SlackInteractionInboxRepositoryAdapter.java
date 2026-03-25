package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class H2SlackInteractionInboxRepositoryAdapter extends SlackInteractionInboxRepositoryAdapter {

    public H2SlackInteractionInboxRepositoryAdapter(
            JPAQueryFactory queryFactory,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaSlackInteractionInboxRepository repository
    ) {
        super(queryFactory, namedParameterJdbcTemplate, repository);
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
                        :processingAttempt
                    )
                ) AS source (
                    interaction_type,
                    idempotency_key,
                    payload_json,
                    status,
                    processing_attempt
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
                        processing_attempt
                    )
                    VALUES (
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        source.interaction_type,
                        source.idempotency_key,
                        source.payload_json,
                        source.status,
                        source.processing_attempt
                    )
                """;
    }
}
