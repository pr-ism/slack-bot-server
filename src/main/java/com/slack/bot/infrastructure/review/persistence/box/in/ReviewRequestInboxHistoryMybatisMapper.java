package com.slack.bot.infrastructure.review.persistence.box.in;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import java.time.Instant;
import java.util.List;
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

@Mapper
public interface ReviewRequestInboxHistoryMybatisMapper {

    @Results(id = "reviewRequestInboxHistoryRowResultMap", value = {})
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "inboxId", javaType = Long.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "status", javaType = ReviewRequestInboxStatus.class),
            @Arg(column = "completedAt", javaType = Instant.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = ReviewRequestInboxFailureType.class)
    })
    @Select("""
            SELECT id,
                   inbox_id AS inboxId,
                   processing_attempt AS processingAttempt,
                   status,
                   completed_at AS completedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_request_inbox_history
            ORDER BY id ASC
            """)
    List<ReviewRequestInboxHistoryRow> findAllRows();

    @ResultMap("reviewRequestInboxHistoryRowResultMap")
    @Select("""
            SELECT id,
                   inbox_id AS inboxId,
                   processing_attempt AS processingAttempt,
                   status,
                   completed_at AS completedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_request_inbox_history
            WHERE inbox_id = #{inboxId}
            ORDER BY id DESC
            """)
    List<ReviewRequestInboxHistoryRow> findRowsByInboxIdOrderByIdDesc(@Param("inboxId") Long inboxId);

    @Insert("""
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
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6),
                #{inboxId},
                #{processingAttempt},
                #{status},
                #{completedAt},
                #{failureReason},
                #{failureType}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReviewRequestInboxHistoryRow row);

    @Delete("""
            DELETE FROM review_request_inbox_history
            """)
    int deleteAll();

    default List<ReviewRequestInboxHistory> findAllDomains() {
        return findAllRows().stream()
                            .map(row -> row.toDomain())
                            .toList();
    }

    default List<ReviewRequestInboxHistory> findDomainsByInboxIdOrderByIdDesc(Long inboxId) {
        return findRowsByInboxIdOrderByIdDesc(inboxId).stream()
                                                     .map(row -> row.toDomain())
                                                     .toList();
    }
}
