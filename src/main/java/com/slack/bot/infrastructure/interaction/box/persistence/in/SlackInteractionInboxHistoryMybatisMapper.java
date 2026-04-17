package com.slack.bot.infrastructure.interaction.box.persistence.in;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface SlackInteractionInboxHistoryMybatisMapper {

    @Insert("""
            INSERT INTO slack_interaction_inbox_history (
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
    int insert(SlackInteractionInboxHistoryRow row);
}
