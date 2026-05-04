package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxHistory;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReviewNotificationOutboxHistoryMybatisMapper {

    @Insert("""
            INSERT INTO review_notification_outbox_history (
                created_at,
                updated_at,
                outbox_id,
                processing_attempt,
                status,
                completed_at,
                failure_reason,
                failure_type
            )
            VALUES (
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6),
                #{outboxId},
                #{processingAttempt},
                #{status},
                #{completedAt},
                #{failureReason},
                #{failureType}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReviewNotificationOutboxHistoryRow row);

    @Results(id = "reviewNotificationOutboxHistoryRowResultMap", value = {})
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "outboxId", javaType = Long.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "status", javaType = ReviewNotificationOutboxStatus.class),
            @Arg(column = "completedAt", javaType = Instant.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = SlackInteractionFailureType.class)
    })
    @Select("""
            SELECT id,
                   outbox_id AS outboxId,
                   processing_attempt AS processingAttempt,
                   status,
                   completed_at AS completedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_notification_outbox_history
            ORDER BY id ASC
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<ReviewNotificationOutboxHistoryRow> findAllRows();

    default List<ReviewNotificationOutboxHistory> findAllDomains() {
        return findAllRows().stream()
                            .map(row -> row.toDomain())
                            .toList();
    }
}
