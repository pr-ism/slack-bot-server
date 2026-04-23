package com.slack.bot.infrastructure.review.persistence.box.in;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class H2ReviewRequestInboxRepositoryAdapter extends ReviewRequestInboxRepositoryAdapter {

    public H2ReviewRequestInboxRepositoryAdapter(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            ReviewRequestInboxMybatisMapper reviewRequestInboxMybatisMapper,
            ReviewRequestInboxHistoryMybatisMapper reviewRequestInboxHistoryMybatisMapper
    ) {
        super(
                namedParameterJdbcTemplate,
                reviewRequestInboxMybatisMapper,
                reviewRequestInboxHistoryMybatisMapper
        );
    }

    @Override
    protected String buildUpsertPendingSql() {
        return """
                MERGE INTO review_request_inbox AS target
                USING (
                    VALUES (
                        :idempotencyKey,
                        :apiKey,
                        :githubPullRequestId,
                        :requestJson,
                        :availableAt,
                        :pendingStatus
                    )
                ) AS source (
                    idempotency_key,
                    api_key,
                    github_pull_request_id,
                    request_json,
                    available_at,
                    pending_status
                )
                ON target.idempotency_key = source.idempotency_key
                WHEN MATCHED AND target.status IN (:pendingStatus, :retryPendingStatus) THEN
                    UPDATE SET
                        updated_at = CURRENT_TIMESTAMP(6),
                        api_key = source.api_key,
                        github_pull_request_id = source.github_pull_request_id,
                        request_json = source.request_json,
                        available_at = source.available_at,
                        status = source.pending_status,
                        processing_attempt = 0,
                        processing_started_at = :noProcessingStartedAt,
                        processed_at = :noProcessedAt,
                        failed_at = :noFailureAt,
                        failure_reason = :noFailureReason,
                        failure_type = :noneFailureType
                WHEN NOT MATCHED THEN
                    INSERT (
                        created_at,
                        updated_at,
                        idempotency_key,
                        api_key,
                        github_pull_request_id,
                        request_json,
                        available_at,
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
                        source.idempotency_key,
                        source.api_key,
                        source.github_pull_request_id,
                        source.request_json,
                        source.available_at,
                        source.pending_status,
                        0,
                        :noProcessingStartedAt,
                        :noProcessedAt,
                        :noFailureAt,
                        :noFailureReason,
                        :noneFailureType
                    )
                """;
    }
}
