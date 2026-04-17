package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.common.BoxEventTimeState;
import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.common.BoxProcessingLeaseState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxFieldState;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SlackNotificationOutboxMybatisMapper {

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "messageType", javaType = SlackNotificationOutboxMessageType.class),
            @Arg(column = "idempotencyKey", javaType = String.class),
            @Arg(column = "teamId", javaType = String.class),
            @Arg(column = "channelId", javaType = String.class),
            @Arg(column = "userIdState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "userId", javaType = String.class),
            @Arg(column = "textState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "text", javaType = String.class),
            @Arg(column = "blocksJsonState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "blocksJson", javaType = String.class),
            @Arg(column = "fallbackTextState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "fallbackText", javaType = String.class),
            @Arg(column = "status", javaType = SlackNotificationOutboxStatus.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "processingLeaseState", javaType = BoxProcessingLeaseState.class),
            @Arg(column = "processingStartedAt", javaType = Instant.class),
            @Arg(column = "sentTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "sentAt", javaType = Instant.class),
            @Arg(column = "failedTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "failedAt", javaType = Instant.class),
            @Arg(column = "failureState", javaType = BoxFailureState.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = SlackInteractionFailureType.class)
    })
    @Select("""
            SELECT id,
                   message_type AS messageType,
                   idempotency_key AS idempotencyKey,
                   team_id AS teamId,
                   channel_id AS channelId,
                   user_id_state AS userIdState,
                   user_id AS userId,
                   text_state AS textState,
                   text,
                   blocks_json_state AS blocksJsonState,
                   blocks_json AS blocksJson,
                   fallback_text_state AS fallbackTextState,
                   fallback_text AS fallbackText,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_lease_state AS processingLeaseState,
                   processing_started_at AS processingStartedAt,
                   sent_time_state AS sentTimeState,
                   sent_at AS sentAt,
                   failed_time_state AS failedTimeState,
                   failed_at AS failedAt,
                   failure_state AS failureState,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM slack_notification_outbox
            WHERE id = #{id}
            """)
    SlackNotificationOutboxRow findRowById(@Param("id") Long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "messageType", javaType = SlackNotificationOutboxMessageType.class),
            @Arg(column = "idempotencyKey", javaType = String.class),
            @Arg(column = "teamId", javaType = String.class),
            @Arg(column = "channelId", javaType = String.class),
            @Arg(column = "userIdState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "userId", javaType = String.class),
            @Arg(column = "textState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "text", javaType = String.class),
            @Arg(column = "blocksJsonState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "blocksJson", javaType = String.class),
            @Arg(column = "fallbackTextState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "fallbackText", javaType = String.class),
            @Arg(column = "status", javaType = SlackNotificationOutboxStatus.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "processingLeaseState", javaType = BoxProcessingLeaseState.class),
            @Arg(column = "processingStartedAt", javaType = Instant.class),
            @Arg(column = "sentTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "sentAt", javaType = Instant.class),
            @Arg(column = "failedTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "failedAt", javaType = Instant.class),
            @Arg(column = "failureState", javaType = BoxFailureState.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = SlackInteractionFailureType.class)
    })
    @Select("""
            SELECT id,
                   message_type AS messageType,
                   idempotency_key AS idempotencyKey,
                   team_id AS teamId,
                   channel_id AS channelId,
                   user_id_state AS userIdState,
                   user_id AS userId,
                   text_state AS textState,
                   text,
                   blocks_json_state AS blocksJsonState,
                   blocks_json AS blocksJson,
                   fallback_text_state AS fallbackTextState,
                   fallback_text AS fallbackText,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_lease_state AS processingLeaseState,
                   processing_started_at AS processingStartedAt,
                   sent_time_state AS sentTimeState,
                   sent_at AS sentAt,
                   failed_time_state AS failedTimeState,
                   failed_at AS failedAt,
                   failure_state AS failureState,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM slack_notification_outbox
            WHERE id = #{id}
            FOR UPDATE
            """)
    Optional<SlackNotificationOutboxRow> findRowForUpdateById(@Param("id") Long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "messageType", javaType = SlackNotificationOutboxMessageType.class),
            @Arg(column = "idempotencyKey", javaType = String.class),
            @Arg(column = "teamId", javaType = String.class),
            @Arg(column = "channelId", javaType = String.class),
            @Arg(column = "userIdState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "userId", javaType = String.class),
            @Arg(column = "textState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "text", javaType = String.class),
            @Arg(column = "blocksJsonState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "blocksJson", javaType = String.class),
            @Arg(column = "fallbackTextState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "fallbackText", javaType = String.class),
            @Arg(column = "status", javaType = SlackNotificationOutboxStatus.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "processingLeaseState", javaType = BoxProcessingLeaseState.class),
            @Arg(column = "processingStartedAt", javaType = Instant.class),
            @Arg(column = "sentTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "sentAt", javaType = Instant.class),
            @Arg(column = "failedTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "failedAt", javaType = Instant.class),
            @Arg(column = "failureState", javaType = BoxFailureState.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = SlackInteractionFailureType.class)
    })
    @Select({
            "<script>",
            "SELECT id,",
            "       message_type AS messageType,",
            "       idempotency_key AS idempotencyKey,",
            "       team_id AS teamId,",
            "       channel_id AS channelId,",
            "       user_id_state AS userIdState,",
            "       user_id AS userId,",
            "       text_state AS textState,",
            "       text,",
            "       blocks_json_state AS blocksJsonState,",
            "       blocks_json AS blocksJson,",
            "       fallback_text_state AS fallbackTextState,",
            "       fallback_text AS fallbackText,",
            "       status,",
            "       processing_attempt AS processingAttempt,",
            "       processing_lease_state AS processingLeaseState,",
            "       processing_started_at AS processingStartedAt,",
            "       sent_time_state AS sentTimeState,",
            "       sent_at AS sentAt,",
            "       failed_time_state AS failedTimeState,",
            "       failed_at AS failedAt,",
            "       failure_state AS failureState,",
            "       failure_reason AS failureReason,",
            "       failure_type AS failureType",
            "FROM slack_notification_outbox",
            "WHERE status IN",
            "  <foreach collection='claimableStatuses' item='claimableStatus' open='(' separator=',' close=')'>",
            "    #{claimableStatus}",
            "  </foreach>",
            "  <if test='excludedOutboxIds != null and excludedOutboxIds.size &gt; 0'>",
            "    AND id NOT IN",
            "    <foreach collection='excludedOutboxIds' item='excludedOutboxId' open='(' separator=',' close=')'>",
            "      #{excludedOutboxId}",
            "    </foreach>",
            "  </if>",
            "ORDER BY id ASC",
            "LIMIT 1",
            "FOR UPDATE SKIP LOCKED",
            "</script>"
    })
    Optional<SlackNotificationOutboxRow> findClaimableRowForUpdate(
            @Param("claimableStatuses") List<String> claimableStatuses,
            @Param("excludedOutboxIds") Collection<Long> excludedOutboxIds
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "messageType", javaType = SlackNotificationOutboxMessageType.class),
            @Arg(column = "idempotencyKey", javaType = String.class),
            @Arg(column = "teamId", javaType = String.class),
            @Arg(column = "channelId", javaType = String.class),
            @Arg(column = "userIdState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "userId", javaType = String.class),
            @Arg(column = "textState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "text", javaType = String.class),
            @Arg(column = "blocksJsonState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "blocksJson", javaType = String.class),
            @Arg(column = "fallbackTextState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "fallbackText", javaType = String.class),
            @Arg(column = "status", javaType = SlackNotificationOutboxStatus.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "processingLeaseState", javaType = BoxProcessingLeaseState.class),
            @Arg(column = "processingStartedAt", javaType = Instant.class),
            @Arg(column = "sentTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "sentAt", javaType = Instant.class),
            @Arg(column = "failedTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "failedAt", javaType = Instant.class),
            @Arg(column = "failureState", javaType = BoxFailureState.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = SlackInteractionFailureType.class)
    })
    @Select({
            "<script>",
            "SELECT id,",
            "       message_type AS messageType,",
            "       idempotency_key AS idempotencyKey,",
            "       team_id AS teamId,",
            "       channel_id AS channelId,",
            "       user_id_state AS userIdState,",
            "       user_id AS userId,",
            "       text_state AS textState,",
            "       text,",
            "       blocks_json_state AS blocksJsonState,",
            "       blocks_json AS blocksJson,",
            "       fallback_text_state AS fallbackTextState,",
            "       fallback_text AS fallbackText,",
            "       status,",
            "       processing_attempt AS processingAttempt,",
            "       processing_lease_state AS processingLeaseState,",
            "       processing_started_at AS processingStartedAt,",
            "       sent_time_state AS sentTimeState,",
            "       sent_at AS sentAt,",
            "       failed_time_state AS failedTimeState,",
            "       failed_at AS failedAt,",
            "       failure_state AS failureState,",
            "       failure_reason AS failureReason,",
            "       failure_type AS failureType",
            "FROM slack_notification_outbox",
            "WHERE status = #{processingStatus}",
            "  AND processing_started_at &lt; #{processingStartedBefore}",
            "ORDER BY processing_started_at ASC, id ASC",
            "LIMIT #{recoveryBatchSize}",
            "FOR UPDATE SKIP LOCKED",
            "</script>"
    })
    List<SlackNotificationOutboxRow> findTimeoutRecoveryRowsForUpdate(
            @Param("processingStatus") String processingStatus,
            @Param("processingStartedBefore") Instant processingStartedBefore,
            @Param("recoveryBatchSize") int recoveryBatchSize
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "messageType", javaType = SlackNotificationOutboxMessageType.class),
            @Arg(column = "idempotencyKey", javaType = String.class),
            @Arg(column = "teamId", javaType = String.class),
            @Arg(column = "channelId", javaType = String.class),
            @Arg(column = "userIdState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "userId", javaType = String.class),
            @Arg(column = "textState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "text", javaType = String.class),
            @Arg(column = "blocksJsonState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "blocksJson", javaType = String.class),
            @Arg(column = "fallbackTextState", javaType = SlackNotificationOutboxFieldState.class),
            @Arg(column = "fallbackText", javaType = String.class),
            @Arg(column = "status", javaType = SlackNotificationOutboxStatus.class),
            @Arg(column = "processingAttempt", javaType = int.class),
            @Arg(column = "processingLeaseState", javaType = BoxProcessingLeaseState.class),
            @Arg(column = "processingStartedAt", javaType = Instant.class),
            @Arg(column = "sentTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "sentAt", javaType = Instant.class),
            @Arg(column = "failedTimeState", javaType = BoxEventTimeState.class),
            @Arg(column = "failedAt", javaType = Instant.class),
            @Arg(column = "failureState", javaType = BoxFailureState.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "failureType", javaType = SlackInteractionFailureType.class)
    })
    @Select("""
            SELECT id,
                   message_type AS messageType,
                   idempotency_key AS idempotencyKey,
                   team_id AS teamId,
                   channel_id AS channelId,
                   user_id_state AS userIdState,
                   user_id AS userId,
                   text_state AS textState,
                   text,
                   blocks_json_state AS blocksJsonState,
                   blocks_json AS blocksJson,
                   fallback_text_state AS fallbackTextState,
                   fallback_text AS fallbackText,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_lease_state AS processingLeaseState,
                   processing_started_at AS processingStartedAt,
                   sent_time_state AS sentTimeState,
                   sent_at AS sentAt,
                   failed_time_state AS failedTimeState,
                   failed_at AS failedAt,
                   failure_state AS failureState,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM slack_notification_outbox
            ORDER BY id ASC
            """)
    List<SlackNotificationOutboxRow> findAllRows();

    @Insert("""
            INSERT INTO slack_notification_outbox (
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
                processing_started_at,
                sent_time_state,
                sent_at,
                failed_time_state,
                failed_at,
                failure_state,
                failure_reason,
                failure_type
            )
            VALUES (
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6),
                #{messageType},
                #{idempotencyKey},
                #{teamId},
                #{channelId},
                #{userIdState},
                #{userId},
                #{textState},
                #{text},
                #{blocksJsonState},
                #{blocksJson},
                #{fallbackTextState},
                #{fallbackText},
                #{status},
                #{processingAttempt},
                #{processingLeaseState},
                #{processingStartedAt},
                #{sentTimeState},
                #{sentAt},
                #{failedTimeState},
                #{failedAt},
                #{failureState},
                #{failureReason},
                #{failureType}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SlackNotificationOutboxRow row);

    @Update("""
            UPDATE slack_notification_outbox
            SET updated_at = CURRENT_TIMESTAMP(6),
                message_type = #{messageType},
                idempotency_key = #{idempotencyKey},
                team_id = #{teamId},
                channel_id = #{channelId},
                user_id_state = #{userIdState},
                user_id = #{userId},
                text_state = #{textState},
                text = #{text},
                blocks_json_state = #{blocksJsonState},
                blocks_json = #{blocksJson},
                fallback_text_state = #{fallbackTextState},
                fallback_text = #{fallbackText},
                status = #{status},
                processing_attempt = #{processingAttempt},
                processing_lease_state = #{processingLeaseState},
                processing_started_at = #{processingStartedAt},
                sent_time_state = #{sentTimeState},
                sent_at = #{sentAt},
                failed_time_state = #{failedTimeState},
                failed_at = #{failedAt},
                failure_state = #{failureState},
                failure_reason = #{failureReason},
                failure_type = #{failureType}
            WHERE id = #{id}
            """)
    int update(SlackNotificationOutboxRow row);

    default Optional<SlackNotificationOutbox> findDomainById(Long id) {
        return Optional.ofNullable(findRowById(id))
                       .map(row -> row.toDomain());
    }

    default List<SlackNotificationOutbox> findAllDomains() {
        return findAllRows().stream()
                            .map(row -> row.toDomain())
                            .toList();
    }
}
