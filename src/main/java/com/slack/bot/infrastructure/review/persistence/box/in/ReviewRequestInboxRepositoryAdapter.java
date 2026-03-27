package com.slack.bot.infrastructure.review.persistence.box.in;

import static com.slack.bot.infrastructure.review.box.in.QReviewRequestInbox.reviewRequestInbox;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewRequestInboxRepositoryAdapter implements ReviewRequestInboxRepository {

    private static final List<ReviewRequestInboxStatus> CLAIMABLE_STATUSES = List.of(
            ReviewRequestInboxStatus.PENDING,
            ReviewRequestInboxStatus.RETRY_PENDING
    );
    private static final long TIMEOUT_RECOVERY_BATCH_SIZE = 100L;

    private final JPAQueryFactory queryFactory;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JpaReviewRequestInboxRepository reviewRequestInboxJpaRepository;
    private final JpaReviewRequestInboxHistoryRepository reviewRequestInboxHistoryJpaRepository;

    @Override
    @Transactional
    public void upsertPending(
            String idempotencyKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRequestJson(requestJson);
        validateAvailableAt(availableAt);

        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                idempotencyKey,
                apiKey,
                githubPullRequestId,
                requestJson,
                availableAt
        );

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("idempotencyKey", inbox.getIdempotencyKey())
                .addValue("apiKey", inbox.getApiKey())
                .addValue("githubPullRequestId", inbox.getGithubPullRequestId())
                .addValue("requestJson", inbox.getRequestJson())
                .addValue("availableAt", Timestamp.from(inbox.getAvailableAt()))
                .addValue("pendingStatus", ReviewRequestInboxStatus.PENDING.name())
                .addValue("retryPendingStatus", ReviewRequestInboxStatus.RETRY_PENDING.name())
                .addValue("noProcessingStartedAt", Timestamp.from(FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT))
                .addValue("noProcessedAt", Timestamp.from(FailureSnapshotDefaults.NO_PROCESSED_AT))
                .addValue("noFailureAt", Timestamp.from(FailureSnapshotDefaults.NO_FAILURE_AT))
                .addValue("noFailureReason", FailureSnapshotDefaults.NO_FAILURE_REASON)
                .addValue("noneFailureType", ReviewRequestInboxFailureType.NONE.name());

        namedParameterJdbcTemplate.update(
                buildUpsertPendingSql(),
                parameters
        );
    }

    @Override
    @Transactional
    public Optional<Long> claimNextId(
            Instant processingStartedAt,
            Instant availableBeforeOrAt,
            Collection<Long> excludedInboxIds
    ) {
        validateProcessingStartedAt(processingStartedAt);
        validateAvailableAt(availableBeforeOrAt);

        MapSqlParameterSource selectParameters = new MapSqlParameterSource()
                .addValue(
                        "claimableStatuses",
                        List.of(
                                ReviewRequestInboxStatus.PENDING.name(),
                                ReviewRequestInboxStatus.RETRY_PENDING.name()
                        )
                )
                .addValue("availableBeforeOrAt", Timestamp.from(availableBeforeOrAt));
        addExcludedInboxIds(selectParameters, excludedInboxIds);

        List<Long> claimedIds = namedParameterJdbcTemplate.query(
                buildClaimNextIdSelectSql(excludedInboxIds),
                selectParameters,
                (resultSet, rowNum) -> resultSet.getLong(1)
        );
        if (claimedIds.isEmpty()) {
            return Optional.empty();
        }

        Long inboxId = claimedIds.getFirst();
        MapSqlParameterSource updateParameters = new MapSqlParameterSource()
                .addValue("processingStatus", ReviewRequestInboxStatus.PROCESSING.name())
                .addValue("processingStartedAt", Timestamp.from(processingStartedAt))
                .addValue("inboxId", inboxId);

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                    UPDATE review_request_inbox
                    SET status = :processingStatus,
                        processing_attempt = processing_attempt + 1,
                        processing_started_at = :processingStartedAt,
                        processed_at = :noProcessedAt,
                        failed_at = :noFailureAt,
                        failure_reason = :noFailureReason,
                        failure_type = :noneFailureType
                    WHERE id = :inboxId
                    """,
                updateParameters
                        .addValue("noProcessedAt", Timestamp.from(FailureSnapshotDefaults.NO_PROCESSED_AT))
                        .addValue("noFailureAt", Timestamp.from(FailureSnapshotDefaults.NO_FAILURE_AT))
                        .addValue("noFailureReason", FailureSnapshotDefaults.NO_FAILURE_REASON)
                        .addValue("noneFailureType", ReviewRequestInboxFailureType.NONE.name())
        );
        if (updatedCount == 0) {
            return Optional.empty();
        }

        return Optional.of(inboxId);
    }

    @Override
    @Transactional
    public boolean renewProcessingLease(
            Long inboxId,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    ) {
        validateRenewProcessingLeaseArguments(
                inboxId,
                currentProcessingStartedAt,
                renewedProcessingStartedAt
        );

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("renewedProcessingStartedAt", Timestamp.from(renewedProcessingStartedAt))
                .addValue("inboxId", inboxId)
                .addValue("processingStatus", ReviewRequestInboxStatus.PROCESSING.name())
                .addValue("currentProcessingStartedAt", Timestamp.from(currentProcessingStartedAt));

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE review_request_inbox
                SET updated_at = CURRENT_TIMESTAMP(6),
                    processing_started_at = :renewedProcessingStartedAt
                WHERE id = :inboxId
                  AND status = :processingStatus
                  AND processing_started_at = :currentProcessingStartedAt
                """,
                parameters
        );

        return updatedCount > 0;
    }

    private String buildClaimNextIdSelectSql(Collection<Long> excludedInboxIds) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id
                FROM review_request_inbox
                WHERE status IN (:claimableStatuses)
                  AND available_at <= :availableBeforeOrAt
                """
        );

        appendExcludedInboxIdsClause(sql, excludedInboxIds);
        sql.append(
                """
                ORDER BY available_at ASC, id ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """
        );

        return sql.toString();
    }

    private void appendExcludedInboxIdsClause(StringBuilder sql, Collection<Long> excludedInboxIds) {
        if (excludedInboxIds == null || excludedInboxIds.isEmpty()) {
            return;
        }

        sql.append("\n  AND id NOT IN (:excludedInboxIds)");
    }

    private void addExcludedInboxIds(
            MapSqlParameterSource parameters,
            Collection<Long> excludedInboxIds
    ) {
        if (excludedInboxIds == null || excludedInboxIds.isEmpty()) {
            return;
        }

        parameters.addValue("excludedInboxIds", excludedInboxIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewRequestInbox> findById(Long inboxId) {
        return reviewRequestInboxJpaRepository.findById(inboxId);
    }

    @Override
    @Transactional
    public int recoverTimeoutProcessing(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    ) {
        validateProcessingStartedBefore(processingStartedBefore);
        validateFailedAt(failedAt);
        validateFailureReason(failureReason);
        validateMaxAttempts(maxAttempts);

        BooleanExpression timeoutCondition = reviewRequestInbox.processingStartedAt.lt(processingStartedBefore);

        int exhaustedCount = recoverTimeoutProcessingByStatus(
                timeoutCondition,
                reviewRequestInbox.processingAttempt.goe(maxAttempts),
                ReviewRequestInboxStatus.FAILED,
                failedAt,
                failureReason,
                ReviewRequestInboxFailureType.RETRY_EXHAUSTED,
                ReviewRequestInboxFailureType.RETRY_EXHAUSTED
        );
        int recoveredCount = recoverTimeoutProcessingByStatus(
                timeoutCondition,
                reviewRequestInbox.processingAttempt.lt(maxAttempts),
                ReviewRequestInboxStatus.RETRY_PENDING,
                failedAt,
                failureReason,
                ReviewRequestInboxFailureType.NONE,
                ReviewRequestInboxFailureType.PROCESSING_TIMEOUT
        );

        return exhaustedCount + recoveredCount;
    }

    private int recoverTimeoutProcessingByStatus(
            BooleanExpression timeoutCondition,
            BooleanExpression attemptCondition,
            ReviewRequestInboxStatus targetStatus,
            Instant failedAt,
            String failureReason,
            ReviewRequestInboxFailureType snapshotFailureType,
            ReviewRequestInboxFailureType historyFailureType
    ) {
        List<Tuple> timedOutRows = queryFactory
                .select(reviewRequestInbox.id, reviewRequestInbox.processingAttempt)
                .from(reviewRequestInbox)
                .where(
                        reviewRequestInbox.status.eq(ReviewRequestInboxStatus.PROCESSING),
                        timeoutCondition,
                        attemptCondition
                )
                .orderBy(reviewRequestInbox.processingStartedAt.asc(), reviewRequestInbox.id.asc())
                .limit(TIMEOUT_RECOVERY_BATCH_SIZE)
                .fetch();

        if (timedOutRows.isEmpty()) {
            return 0;
        }

        List<Long> inboxIds = new ArrayList<>();
        for (Tuple row : timedOutRows) {
            inboxIds.add(row.get(reviewRequestInbox.id));
        }

        LocalDateTime recoveryUpdatedAt = LocalDateTime.ofInstant(failedAt, ZoneOffset.UTC);
        long updatedCount = queryFactory
                .update(reviewRequestInbox)
                .set(reviewRequestInbox.status, targetStatus)
                .set(reviewRequestInbox.updatedAt, recoveryUpdatedAt)
                .set(
                        reviewRequestInbox.processingStartedAt,
                        FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                )
                .set(reviewRequestInbox.processedAt, FailureSnapshotDefaults.NO_PROCESSED_AT)
                .set(reviewRequestInbox.failedAt, failedAt)
                .set(reviewRequestInbox.failureReason, failureReason)
                .set(reviewRequestInbox.failureType, snapshotFailureType)
                .where(
                        reviewRequestInbox.id.in(inboxIds),
                        reviewRequestInbox.status.eq(ReviewRequestInboxStatus.PROCESSING),
                        timeoutCondition,
                        attemptCondition
                )
                .execute();
        if (updatedCount == 0) {
            return 0;
        }

        List<Long> recoveredInboxIds = queryFactory
                .select(reviewRequestInbox.id)
                .from(reviewRequestInbox)
                .where(
                        reviewRequestInbox.id.in(inboxIds),
                        reviewRequestInbox.status.eq(targetStatus),
                        reviewRequestInbox.updatedAt.eq(recoveryUpdatedAt)
                )
                .fetch();
        if (recoveredInboxIds.isEmpty()) {
            return 0;
        }

        batchInsertTimeoutRecoveryHistory(
                timedOutRows,
                recoveredInboxIds,
                targetStatus,
                failedAt,
                failureReason,
                historyFailureType
        );

        return recoveredInboxIds.size();
    }

    private void batchInsertTimeoutRecoveryHistory(
            List<Tuple> timedOutRows,
            List<Long> recoveredInboxIds,
            ReviewRequestInboxStatus targetStatus,
            Instant failedAt,
            String failureReason,
            ReviewRequestInboxFailureType historyFailureType
    ) {
        Set<Long> recoveredInboxIdSet = new HashSet<>(recoveredInboxIds);
        List<MapSqlParameterSource> batchParameters = new ArrayList<>();

        for (Tuple row : timedOutRows) {
            Long inboxId = row.get(reviewRequestInbox.id);
            if (!recoveredInboxIdSet.contains(inboxId)) {
                continue;
            }

            Integer processingAttempt = row.get(reviewRequestInbox.processingAttempt);
            batchParameters.add(new MapSqlParameterSource()
                    .addValue("createdAt", Timestamp.from(failedAt))
                    .addValue("updatedAt", Timestamp.from(failedAt))
                    .addValue("inboxId", inboxId)
                    .addValue("processingAttempt", processingAttempt)
                    .addValue("status", targetStatus.name())
                    .addValue("completedAt", Timestamp.from(failedAt))
                    .addValue("failureReason", failureReason)
                    .addValue("failureType", historyFailureType.name()));
        }

        namedParameterJdbcTemplate.batchUpdate(
                """
                INSERT INTO review_request_inbox_history (
                    created_at,
                    updated_at,
                    inbox_id,
                    processing_attempt,
                    status,
                    completed_at,
                    failure_reason,
                    failure_type
                )
                VALUES (
                    :createdAt,
                    :updatedAt,
                    :inboxId,
                    :processingAttempt,
                    :status,
                    :completedAt,
                    :failureReason,
                    :failureType
                )
                """,
                batchParameters.toArray(new MapSqlParameterSource[0])
        );
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            ReviewRequestInbox inbox,
            Instant claimedProcessingStartedAt
    ) {
        return saveIfProcessingLeaseMatched(inbox, null, claimedProcessingStartedAt);
    }

    @Override
    @Transactional
    public boolean saveIfProcessingLeaseMatched(
            ReviewRequestInbox inbox,
            ReviewRequestInboxHistory history,
            Instant claimedProcessingStartedAt
    ) {
        validateSaveIfProcessingLeaseMatchedArguments(inbox, claimedProcessingStartedAt);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", inbox.getStatus().name())
                .addValue("processingStartedAt", toTimestamp(inbox.getProcessingStartedAt()))
                .addValue("processedAt", toTimestamp(inbox.getProcessedAt()))
                .addValue("failedAt", toTimestamp(inbox.getFailedAt()))
                .addValue("failureReason", inbox.getFailureReason())
                .addValue("failureType", resolveFailureTypeName(inbox))
                .addValue("inboxId", inbox.getId())
                .addValue("processingStatus", ReviewRequestInboxStatus.PROCESSING.name())
                .addValue("claimedProcessingStartedAt", Timestamp.from(claimedProcessingStartedAt));

        int updatedCount = namedParameterJdbcTemplate.update(
                """
                UPDATE review_request_inbox
                SET updated_at = CURRENT_TIMESTAMP(6),
                    status = :status,
                    processing_started_at = :processingStartedAt,
                    processed_at = :processedAt,
                    failed_at = :failedAt,
                    failure_reason = :failureReason,
                    failure_type = :failureType
                WHERE id = :inboxId
                  AND status = :processingStatus
                  AND processing_started_at = :claimedProcessingStartedAt
                """,
                parameters
        );

        if (updatedCount == 0) {
            return false;
        }

        if (history != null) {
            reviewRequestInboxHistoryJpaRepository.save(history.bindInboxId(inbox.getId()));
        }

        return true;
    }

    @Override
    @Transactional
    public ReviewRequestInbox save(ReviewRequestInbox inbox) {
        return reviewRequestInboxJpaRepository.save(inbox);
    }

    protected String buildUpsertPendingSql() {
        return """
                INSERT INTO review_request_inbox (
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
                    :idempotencyKey,
                    :apiKey,
                    :githubPullRequestId,
                    :requestJson,
                    :availableAt,
                    :pendingStatus,
                    0,
                    :noProcessingStartedAt,
                    :noProcessedAt,
                    :noFailureAt,
                    :noFailureReason,
                    :noneFailureType
                )
                ON DUPLICATE KEY UPDATE
                    updated_at = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        CURRENT_TIMESTAMP(6),
                        updated_at
                    ),
                    api_key = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :apiKey,
                        api_key
                    ),
                    github_pull_request_id = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :githubPullRequestId,
                        github_pull_request_id
                    ),
                    request_json = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :requestJson,
                        request_json
                    ),
                    available_at = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :availableAt,
                        available_at
                    ),
                    status = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :pendingStatus,
                        status
                    ),
                    processing_attempt = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        0,
                        processing_attempt
                    ),
                    processing_started_at = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :noProcessingStartedAt,
                        processing_started_at
                    ),
                    processed_at = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :noProcessedAt,
                        processed_at
                    ),
                    failed_at = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :noFailureAt,
                        failed_at
                    ),
                    failure_reason = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :noFailureReason,
                        failure_reason
                    ),
                    failure_type = IF(
                        status IN (:pendingStatus, :retryPendingStatus),
                        :noneFailureType,
                        failure_type
                    )
                """;
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey는 비어 있을 수 없습니다.");
        }
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
    }

    private void validateGithubPullRequestId(Long githubPullRequestId) {
        if (githubPullRequestId == null || githubPullRequestId <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }
    }

    private void validateRequestJson(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("requestJson은 비어 있을 수 없습니다.");
        }
    }

    private void validateAvailableAt(Instant availableAt) {
        if (availableAt == null) {
            throw new IllegalArgumentException("availableAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateProcessingStartedAt(Instant processingStartedAt) {
        if (processingStartedAt == null) {
            throw new IllegalArgumentException("processingStartedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateRenewProcessingLeaseArguments(
            Long inboxId,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    ) {
        if (inboxId == null) {
            throw new IllegalArgumentException("inboxId는 비어 있을 수 없습니다.");
        }
        validateProcessingStartedAt(currentProcessingStartedAt);
        validateProcessingStartedAt(renewedProcessingStartedAt);
    }

    private void validateProcessingStartedBefore(Instant processingStartedBefore) {
        if (processingStartedBefore == null) {
            throw new IllegalArgumentException("processingStartedBefore는 비어 있을 수 없습니다.");
        }
    }

    private void validateFailedAt(Instant failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }

    private void validateMaxAttempts(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts는 0보다 커야 합니다.");
        }
    }

    private void validateSaveIfProcessingLeaseMatchedArguments(
            ReviewRequestInbox inbox,
            Instant claimedProcessingStartedAt
    ) {
        if (inbox == null) {
            throw new IllegalArgumentException("inbox는 비어 있을 수 없습니다.");
        }
        if (inbox.getId() == null) {
            throw new IllegalArgumentException("inboxId는 비어 있을 수 없습니다.");
        }
        validateProcessingStartedAt(claimedProcessingStartedAt);
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private String resolveFailureTypeName(ReviewRequestInbox inbox) {
        return inbox.getFailureType().name();
    }
}
