package com.slack.bot.infrastructure.interaction.box.persistence.out;

import com.slack.bot.infrastructure.common.BoxFailureState;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SlackNotificationOutboxHistoryMybatisMapper {

    @Insert("""
            INSERT INTO slack_notification_outbox_history (
                created_at,
                updated_at,
                outbox_id,
                processing_attempt,
                status,
                completed_at,
                failure_state,
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
                #{failureState},
                #{failureReason},
                #{failureType}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SlackNotificationOutboxHistoryRow row);

    @Select("""
            SELECT id,
                   outbox_id AS outboxId,
                   processing_attempt AS processingAttempt,
                   status,
                   completed_at AS completedAt,
                   failure_state AS failureState,
                   failure_reason AS failureReason,
                   failure_type AS failureType
            FROM slack_notification_outbox_history
            ORDER BY id ASC
            """)
    List<SlackNotificationOutboxHistoryRow> findAllRows();

    default List<SlackNotificationOutboxHistory> findAllDomains() {
        return findAllRows().stream()
                            .map(row -> row.toDomain())
                            .toList();
    }
}
