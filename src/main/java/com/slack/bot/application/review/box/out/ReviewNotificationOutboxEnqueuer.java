package com.slack.bot.application.review.box.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyKeyGenerator;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyScope;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewNotificationOutboxEnqueuer {

    private final ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;
    private final ReviewNotificationIdempotencyKeyGenerator idempotencyKeyGenerator;

    public void enqueueChannelBlocks(
            String sourceKey,
            String teamId,
            String channelId,
            JsonNode blocks,
            String fallbackText
    ) {
        String normalizedBlocksJson = normalizeBlocks(blocks);
        String idempotencyPayload = idempotencyPayload(sourceKey, teamId, channelId);
        String idempotencyKey = idempotencyKeyGenerator.generate(
                ReviewNotificationIdempotencyScope.REVIEW_NOTIFICATION_OUTBOX,
                idempotencyPayload
        );

        ReviewNotificationOutbox outbox = ReviewNotificationOutbox.builder()
                                                                  .idempotencyKey(idempotencyKey)
                                                                  .teamId(teamId)
                                                                  .channelId(channelId)
                                                                  .blocksJson(normalizedBlocksJson)
                                                                  .fallbackText(fallbackText)
                                                                  .build();

        reviewNotificationOutboxRepository.enqueue(outbox);
    }

    private String normalizeBlocks(JsonNode blocks) {
        if (blocks == null) {
            throw new IllegalArgumentException("blocks는 null일 수 없습니다.");
        }

        return blocks.toString();
    }

    private String idempotencyPayload(String sourceKey, String teamId, String channelId) {
        return "source=" + encodeComponent(sourceKey)
                + "|teamId=" + encodeComponent(teamId)
                + "|channelId=" + encodeComponent(channelId);
    }

    private String encodeComponent(String value) {
        String normalized = nullToEmpty(value);

        return normalized.length() + "#" + normalized;
    }

    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }
}
