package com.slack.bot.application.review.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewNotificationOutboxIdempotencyPayloadEncoder {

    private final ObjectMapper objectMapper;

    public String encode(String sourceKey, String teamId, String channelId) {
        ReviewNotificationOutboxIdempotencySource source = new ReviewNotificationOutboxIdempotencySource(
                nullToEmpty(sourceKey),
                nullToEmpty(teamId),
                nullToEmpty(channelId)
        );

        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("review outbox 멱등성 payload 직렬화에 실패했습니다.", exception);
        }
    }

    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }

    private record ReviewNotificationOutboxIdempotencySource(
            String sourceKey,
            String teamId,
            String channelId
    ) {
    }
}
