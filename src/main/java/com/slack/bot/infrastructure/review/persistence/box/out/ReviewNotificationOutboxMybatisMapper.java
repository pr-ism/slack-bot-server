package com.slack.bot.infrastructure.review.persistence.box.out;

import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxFieldState;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxMessageType;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewNotificationOutboxMybatisMapper {

    @Results(id = "reviewNotificationOutboxRowResultMap", value = {})
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "messageType", javaType = ReviewNotificationOutboxMessageType.class),
            @Arg(column = "idempotencyKey", javaType = String.class),
            @Arg(column = "projectId", javaType = Long.class),
            @Arg(column = "teamId", javaType = String.class),
            @Arg(column = "channelId", javaType = String.class),
            @Arg(column = "payloadJsonState", javaType = ReviewNotificationOutboxFieldState.class),
            @Arg(column = "payloadJson", javaType = String.class),
            @Arg(column = "blocksJsonState", javaType = ReviewNotificationOutboxFieldState.class),
            @Arg(column = "blocksJson", javaType = String.class),
            @Arg(column = "attachmentsJsonState", javaType = ReviewNotificationOutboxFieldState.class),
            @Arg(column = "attachmentsJson", javaType = String.class),
            @Arg(column = "fallbackTextState", javaType = ReviewNotificationOutboxFieldState.class),
            @Arg(column = "fallbackText", javaType = String.class),
            @Arg(column = "status", javaType = ReviewNotificationOutboxStatus.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "processingStartedAt", javaType = Instant.class),
            @Arg(column = "sentAt", javaType = Instant.class),
            @Arg(column = "failedAt", javaType = Instant.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = SlackInteractionFailureType.class)
    })
    @Select("""
            SELECT id,
                   message_type AS messageType,
                   idempotency_key AS idempotencyKey,
                   project_id AS projectId,
                   team_id AS teamId,
                   channel_id AS channelId,
                   payload_json_state AS payloadJsonState,
                   payload_json AS payloadJson,
                   blocks_json_state AS blocksJsonState,
                   blocks_json AS blocksJson,
                   attachments_json_state AS attachmentsJsonState,
                   attachments_json AS attachmentsJson,
                   fallback_text_state AS fallbackTextState,
                   fallback_text AS fallbackText,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   sent_at AS sentAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_notification_outbox
            WHERE id = #{id}
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ReviewNotificationOutboxRow findRowById(@Param("id") Long id);

    @ResultMap("reviewNotificationOutboxRowResultMap")
    @Select("""
            SELECT id,
                   message_type AS messageType,
                   idempotency_key AS idempotencyKey,
                   project_id AS projectId,
                   team_id AS teamId,
                   channel_id AS channelId,
                   payload_json_state AS payloadJsonState,
                   payload_json AS payloadJson,
                   blocks_json_state AS blocksJsonState,
                   blocks_json AS blocksJson,
                   attachments_json_state AS attachmentsJsonState,
                   attachments_json AS attachmentsJson,
                   fallback_text_state AS fallbackTextState,
                   fallback_text AS fallbackText,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   sent_at AS sentAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM review_notification_outbox
            ORDER BY id ASC
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<ReviewNotificationOutboxRow> findAllRows();

    @Insert("""
            INSERT INTO review_notification_outbox (
                created_at,
                updated_at,
                message_type,
                idempotency_key,
                project_id,
                team_id,
                channel_id,
                payload_json_state,
                payload_json,
                blocks_json_state,
                blocks_json,
                attachments_json_state,
                attachments_json,
                fallback_text_state,
                fallback_text,
                status,
                processing_attempt,
                processing_started_at,
                sent_at,
                failed_at,
                failure_reason,
                failure_type
            )
            VALUES (
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6),
                #{messageType},
                #{idempotencyKey},
                #{projectId},
                #{teamId},
                #{channelId},
                #{payloadJsonState},
                #{payloadJson},
                #{blocksJsonState},
                #{blocksJson},
                #{attachmentsJsonState},
                #{attachmentsJson},
                #{fallbackTextState},
                #{fallbackText},
                #{status},
                #{processingAttempt},
                #{processingStartedAt},
                #{sentAt},
                #{failedAt},
                #{failureReason},
                #{failureType}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReviewNotificationOutboxRow row);

    @Update("""
            UPDATE review_notification_outbox
            SET updated_at = CURRENT_TIMESTAMP(6),
                message_type = #{messageType},
                idempotency_key = #{idempotencyKey},
                project_id = #{projectId},
                team_id = #{teamId},
                channel_id = #{channelId},
                payload_json_state = #{payloadJsonState},
                payload_json = #{payloadJson},
                blocks_json_state = #{blocksJsonState},
                blocks_json = #{blocksJson},
                attachments_json_state = #{attachmentsJsonState},
                attachments_json = #{attachmentsJson},
                fallback_text_state = #{fallbackTextState},
                fallback_text = #{fallbackText},
                status = #{status},
                processing_attempt = #{processingAttempt},
                processing_started_at = #{processingStartedAt},
                sent_at = #{sentAt},
                failed_at = #{failedAt},
                failure_reason = #{failureReason},
                failure_type = #{failureType}
            WHERE id = #{id}
            """)
    int update(ReviewNotificationOutboxRow row);

    default Optional<ReviewNotificationOutbox> findDomainById(Long id) {
        return Optional.ofNullable(findRowById(id))
                       .map(row -> row.toDomain());
    }

    default List<ReviewNotificationOutbox> findAllDomains() {
        return findAllRows().stream()
                            .map(row -> row.toDomain())
                            .toList();
    }
}
