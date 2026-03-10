package com.slack.bot.application.interactivity.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.out.exception.OutboxMessageTypeRequiredException;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxIdempotencyPayloadEncoder {

    private final ObjectMapper objectMapper;

    public String encode(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId
    ) {
        if (messageType == null) {
            throw new OutboxMessageTypeRequiredException();
        }

        OutboxIdempotencySource source = new OutboxIdempotencySource(
                nullToEmpty(sourceKey),
                messageType.name(),
                nullToEmpty(teamId),
                nullToEmpty(channelId),
                nullToEmpty(userId)
        );

        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("outbox 멱등성 payload 직렬화에 실패했습니다.", exception);
        }
    }

    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }

    private record OutboxIdempotencySource(
            String sourceKey,
            String messageType,
            String teamId,
            String channelId,
            String userId
    ) {
    }
}
