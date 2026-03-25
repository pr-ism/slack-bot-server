package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class H2SlackNotificationOutboxRepositoryAdapter extends SlackNotificationOutboxRepositoryAdapter {

    public H2SlackNotificationOutboxRepositoryAdapter(
            JPAQueryFactory queryFactory,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaSlackNotificationOutboxRepository repository
    ) {
        super(queryFactory, namedParameterJdbcTemplate, repository);
    }

    @Override
    protected String buildEnqueueSql() {
        return """
                MERGE INTO slack_notification_outbox AS target
                USING (
                    VALUES (
                        :messageType,
                        :idempotencyKey,
                        :teamId,
                        :channelId,
                        :userId,
                        :text,
                        :blocksJson,
                        :fallbackText,
                        :pendingStatus,
                        :processingAttempt
                    )
                ) AS source (
                    message_type,
                    idempotency_key,
                    team_id,
                    channel_id,
                    user_id,
                    text,
                    blocks_json,
                    fallback_text,
                    status,
                    processing_attempt
                )
                ON target.idempotency_key = source.idempotency_key
                WHEN NOT MATCHED THEN
                    INSERT (
                        created_at,
                        updated_at,
                        message_type,
                        idempotency_key,
                        team_id,
                        channel_id,
                        user_id,
                        text,
                        blocks_json,
                        fallback_text,
                        status,
                        processing_attempt
                    )
                    VALUES (
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        source.message_type,
                        source.idempotency_key,
                        source.team_id,
                        source.channel_id,
                        source.user_id,
                        source.text,
                        source.blocks_json,
                        source.fallback_text,
                        source.status,
                        source.processing_attempt
                    )
                """;
    }
}
