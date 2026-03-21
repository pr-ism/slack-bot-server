package com.slack.bot.application.review.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyKeyGenerator;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyScope;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewNotificationOutboxEnqueuer {

    private final ObjectMapper objectMapper;
    private final ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;
    private final ReviewNotificationIdempotencyKeyGenerator idempotencyKeyGenerator;

    public void enqueueReviewNotification(
            String sourceKey,
            Long projectId,
            String teamId,
            String channelId,
            ReviewNotificationPayload payload
    ) {
        validatePayload(payload);
        String payloadJson = serializePayload(payload);
        String idempotencyPayload = idempotencyPayload(sourceKey, teamId, channelId);
        String idempotencyKey = idempotencyKeyGenerator.generate(
                ReviewNotificationIdempotencyScope.REVIEW_NOTIFICATION_OUTBOX,
                idempotencyPayload
        );

        ReviewNotificationOutbox outbox = ReviewNotificationOutbox.builder()
                                                                  .idempotencyKey(idempotencyKey)
                                                                  .projectId(projectId)
                                                                  .teamId(teamId)
                                                                  .channelId(channelId)
                                                                  .payloadJson(payloadJson)
                                                                  .build();

        reviewNotificationOutboxRepository.enqueue(outbox);
    }

    public void enqueueChannelBlocks(
            String sourceKey,
            String teamId,
            String channelId,
            JsonNode blocks,
            JsonNode attachments,
            String fallbackText
    ) {
        String normalizedBlocksJson = normalizeBlocks(blocks);
        String normalizedAttachmentsJson = normalizeAttachments(attachments);
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
                                                                  .attachmentsJson(normalizedAttachmentsJson)
                                                                  .fallbackText(fallbackText)
                                                                  .build();

        reviewNotificationOutboxRepository.enqueue(outbox);
    }

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
                                                                  .attachmentsJson(null)
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

    private String normalizeAttachments(JsonNode attachments) {
        if (attachments == null) {
            return null;
        }

        return attachments.toString();
    }

    private String serializePayload(ReviewNotificationPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("review notification payload 직렬화에 실패했습니다.", exception);
        }
    }

    private void validatePayload(ReviewNotificationPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload는 null일 수 없습니다.");
        }
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
