package com.slack.bot.infrastructure.interaction.box.persistence.out;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class H2SlackNotificationOutboxRepositoryAdapter extends SlackNotificationOutboxRepositoryAdapter {

    public H2SlackNotificationOutboxRepositoryAdapter(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            SlackNotificationOutboxMybatisMapper slackNotificationOutboxMybatisMapper,
            SlackNotificationOutboxHistoryMybatisMapper slackNotificationOutboxHistoryMybatisMapper
    ) {
        super(
                namedParameterJdbcTemplate,
                slackNotificationOutboxMybatisMapper,
                slackNotificationOutboxHistoryMybatisMapper
        );
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
                        :userIdState,
                        :userId,
                        :textState,
                        :text,
                        :blocksJsonState,
                        :blocksJson,
                        :fallbackTextState,
                        :fallbackText,
                        :pendingStatus,
                        :processingAttempt,
                        :processingLeaseState,
                        :sentTimeState,
                        :failedTimeState,
                        :failureState
                    )
                ) AS source (
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
                    sent_time_state,
                    failed_time_state,
                    failure_state
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
                        sent_time_state,
                        failed_time_state,
                        failure_state
                    )
                    VALUES (
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        source.message_type,
                        source.idempotency_key,
                        source.team_id,
                        source.channel_id,
                        source.user_id_state,
                        source.user_id,
                        source.text_state,
                        source.text,
                        source.blocks_json_state,
                        source.blocks_json,
                        source.fallback_text_state,
                        source.fallback_text,
                        source.status,
                        source.processing_attempt,
                        source.processing_lease_state,
                        source.sent_time_state,
                        source.failed_time_state,
                        source.failure_state
                    )
                """;
    }
}
