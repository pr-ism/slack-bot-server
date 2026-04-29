package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewRequestInboxMybatisMapper {

    @Results(id = "reviewRequestInboxRowResultMap", value = {})
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "idempotencyKey", javaType = String.class),
            @Arg(column = "apiKey", javaType = String.class),
            @Arg(column = "githubPullRequestId", javaType = Long.class),
            @Arg(column = "requestJson", javaType = String.class),
            @Arg(column = "availableAt", javaType = Instant.class),
            @Arg(column = "status", javaType = ReviewRequestInboxStatus.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "processingStartedAt", javaType = Instant.class),
            @Arg(column = "processedAt", javaType = Instant.class),
            @Arg(column = "failedAt", javaType = Instant.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = ReviewRequestInboxFailureType.class)
    })
    @Select("""
            SELECT id,
                   idempotency_key AS idempotencyKey,
                   api_key AS apiKey,
                   github_pull_request_id AS githubPullRequestId,
                   request_json AS requestJson,
                   available_at AS availableAt,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   processed_at AS processedAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_request_inbox
            WHERE id = #{id}
            """)
    ReviewRequestInboxRow findRowById(@Param("id") Long id);

    @ResultMap("reviewRequestInboxRowResultMap")
    @Select("""
            <script>
            SELECT id,
                   idempotency_key AS idempotencyKey,
                   api_key AS apiKey,
                   github_pull_request_id AS githubPullRequestId,
                   request_json AS requestJson,
                   available_at AS availableAt,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   processed_at AS processedAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_request_inbox
            WHERE status IN
              <foreach collection='claimableStatuses' item='claimableStatus' open='(' separator=',' close=')'>
                #{claimableStatus}
              </foreach>
              AND available_at &lt;= #{availableBeforeOrAt}
              <if test='excludedInboxIds != null and excludedInboxIds.size &gt; 0'>
                AND id NOT IN
                <foreach collection='excludedInboxIds' item='excludedInboxId' open='(' separator=',' close=')'>
                  #{excludedInboxId}
                </foreach>
              </if>
            ORDER BY available_at ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            </script>
            """)
    Optional<ReviewRequestInboxRow> selectClaimableRowForUpdate(
            @Param("claimableStatuses") List<String> claimableStatuses,
            @Param("availableBeforeOrAt") Instant availableBeforeOrAt,
            @Param("excludedInboxIds") Collection<Long> excludedInboxIds
    );

    @ResultMap("reviewRequestInboxRowResultMap")
    @Select("""
            <script>
            SELECT id,
                   idempotency_key AS idempotencyKey,
                   api_key AS apiKey,
                   github_pull_request_id AS githubPullRequestId,
                   request_json AS requestJson,
                   available_at AS availableAt,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   processed_at AS processedAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_request_inbox
            WHERE status = #{processingStatus}
              AND processing_started_at &lt; #{processingStartedBefore}
            ORDER BY processing_started_at ASC, id ASC
            LIMIT #{recoveryBatchSize}
            FOR UPDATE SKIP LOCKED
            </script>
            """)
    List<ReviewRequestInboxRow> findTimeoutRecoveryRowsForUpdate(
            @Param("processingStatus") String processingStatus,
            @Param("processingStartedBefore") Instant processingStartedBefore,
            @Param("recoveryBatchSize") int recoveryBatchSize
    );

    @ResultMap("reviewRequestInboxRowResultMap")
    @Select("""
            SELECT id,
                   idempotency_key AS idempotencyKey,
                   api_key AS apiKey,
                   github_pull_request_id AS githubPullRequestId,
                   request_json AS requestJson,
                   available_at AS availableAt,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   processed_at AS processedAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_request_inbox
            ORDER BY id ASC
            """)
    List<ReviewRequestInboxRow> findAllRows();

    @Insert("""
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
                #{idempotencyKey},
                #{apiKey},
                #{githubPullRequestId},
                #{requestJson},
                #{availableAt},
                #{status},
                #{processingAttempt},
                #{processingStartedAt},
                #{processedAt},
                #{failedAt},
                #{failureReason},
                #{failureType}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReviewRequestInboxRow row);

    @Update("""
            UPDATE review_request_inbox
            SET updated_at = CURRENT_TIMESTAMP(6),
                idempotency_key = #{idempotencyKey},
                api_key = #{apiKey},
                github_pull_request_id = #{githubPullRequestId},
                request_json = #{requestJson},
                available_at = #{availableAt},
                status = #{status},
                processing_attempt = #{processingAttempt},
                processing_started_at = #{processingStartedAt},
                processed_at = #{processedAt},
                failed_at = #{failedAt},
                failure_reason = #{failureReason},
                failure_type = #{failureType}
            WHERE id = #{id}
            """)
    int update(ReviewRequestInboxRow row);

    @Delete("""
            DELETE FROM review_request_inbox
            """)
    int deleteAll();

    default Optional<ReviewRequestInbox> findDomainById(Long id) {
        return Optional.ofNullable(findRowById(id))
                       .map(row -> row.toDomain());
    }

    default Optional<ReviewRequestInboxRow> findClaimableRowForUpdate(
            List<String> claimableStatuses,
            Instant availableBeforeOrAt,
            Collection<Long> excludedInboxIds
    ) {
        if (claimableStatuses == null || claimableStatuses.isEmpty()) {
            return Optional.empty();
        }

        return selectClaimableRowForUpdate(
                claimableStatuses,
                availableBeforeOrAt,
                excludedInboxIds
        );
    }

    default List<ReviewRequestInbox> findAllDomains() {
        return findAllRows().stream()
                            .map(row -> row.toDomain())
                            .toList();
    }
}
