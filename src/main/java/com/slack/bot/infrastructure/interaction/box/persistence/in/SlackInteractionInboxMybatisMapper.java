package com.slack.bot.infrastructure.interaction.box.persistence.in;

import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SlackInteractionInboxMybatisMapper {

    @Select("""
            SELECT id,
                   interaction_type AS interactionType,
                   idempotency_key AS idempotencyKey,
                   payload_json AS payloadJson,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   processed_at AS processedAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM slack_interaction_inbox
            WHERE id = #{id}
            """)
    SlackInteractionInboxRow findRowById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   interaction_type AS interactionType,
                   idempotency_key AS idempotencyKey,
                   payload_json AS payloadJson,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   processed_at AS processedAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM slack_interaction_inbox
            WHERE id = #{id}
            FOR UPDATE
            """)
    SlackInteractionInboxRow findRowByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT id,
                   interaction_type AS interactionType,
                   idempotency_key AS idempotencyKey,
                   payload_json AS payloadJson,
                   status,
                   processing_attempt AS processingAttempt,
                   processing_started_at AS processingStartedAt,
                   processed_at AS processedAt,
                   failed_at AS failedAt,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM slack_interaction_inbox
            ORDER BY id ASC
            """)
    List<SlackInteractionInboxRow> findAllRows();

    @Insert("""
            INSERT INTO slack_interaction_inbox (
                created_at,
                updated_at,
                interaction_type,
                idempotency_key,
                payload_json,
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
                #{interactionType},
                #{idempotencyKey},
                #{payloadJson},
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
    int insert(SlackInteractionInboxRow row);

    @Update("""
            UPDATE slack_interaction_inbox
            SET updated_at = CURRENT_TIMESTAMP(6),
                interaction_type = #{interactionType},
                idempotency_key = #{idempotencyKey},
                payload_json = #{payloadJson},
                status = #{status},
                processing_attempt = #{processingAttempt},
                processing_started_at = #{processingStartedAt},
                processed_at = #{processedAt},
                failed_at = #{failedAt},
                failure_reason = #{failureReason},
                failure_type = #{failureType}
            WHERE id = #{id}
            """)
    int update(SlackInteractionInboxRow row);

    default Optional<SlackInteractionInbox> findDomainById(Long id) {
        return Optional.ofNullable(findRowById(id))
                       .map(row -> row.toDomain());
    }

    default Optional<SlackInteractionInbox> findLockedDomainById(Long id) {
        return Optional.ofNullable(findRowByIdForUpdate(id))
                       .map(row -> row.toDomain());
    }

    default List<SlackInteractionInbox> findAllDomains() {
        return findAllRows().stream()
                            .map(row -> row.toDomain())
                            .toList();
    }
}
