package com.slack.bot.application.review.box.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewRequestInboxIdempotencyPayloadEncoder {

    private final ObjectMapper objectMapper;

    public String encode(String apiKey, ReviewNotificationPayload request) {
        ReviewRequestInboxIdempotencySource source = new ReviewRequestInboxIdempotencySource(
                nullToEmpty(apiKey),
                request.githubPullRequestId(),
                nullToEmpty(request.reviewRoundKey())
        );

        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("review request inbox 멱등성 payload 직렬화에 실패했습니다.", exception);
        }
    }

    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }

    private record ReviewRequestInboxIdempotencySource(
            String apiKey,
            Long githubPullRequestId,
            String reviewRoundKey
    ) {
    }
}
