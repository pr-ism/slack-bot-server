package com.slack.bot.infrastructure.review.persistence.box.out;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class H2ReviewNotificationOutboxRepositoryAdapter extends ReviewNotificationOutboxRepositoryAdapter {

    public H2ReviewNotificationOutboxRepositoryAdapter(
            JPAQueryFactory queryFactory,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JpaReviewNotificationOutboxRepository repository,
            JpaReviewNotificationOutboxHistoryRepository historyRepository
    ) {
        super(queryFactory, namedParameterJdbcTemplate, repository, historyRepository);
    }

    @Override
    protected String buildEnqueueSql() {
        return """
                MERGE INTO review_notification_outbox AS target
                USING (
                    VALUES (
                        :idempotencyKey,
                        :projectId,
                        :teamId,
                        :channelId,
                        :payloadJson,
                        :blocksJson,
                        :attachmentsJson,
                        :fallbackText,
                        :pendingStatus,
                        :processingAttempt
                    )
                ) AS source (
                    idempotency_key,
                    project_id,
                    team_id,
                    channel_id,
                    payload_json,
                    blocks_json,
                    attachments_json,
                    fallback_text,
                    status,
                    processing_attempt
                )
                ON target.idempotency_key = source.idempotency_key
                WHEN NOT MATCHED THEN
                    INSERT (
                        created_at,
                        updated_at,
                        idempotency_key,
                        project_id,
                        team_id,
                        channel_id,
                        payload_json,
                        blocks_json,
                        attachments_json,
                        fallback_text,
                        status,
                        processing_attempt
                    )
                    VALUES (
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        source.idempotency_key,
                        source.project_id,
                        source.team_id,
                        source.channel_id,
                        source.payload_json,
                        source.blocks_json,
                        source.attachments_json,
                        source.fallback_text,
                        source.status,
                        source.processing_attempt
                    )
                """;
    }
}
