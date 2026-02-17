package com.slack.bot.application.interactivity.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyScope;
import com.slack.bot.application.interactivity.box.aop.InteractivityImmediateTriggerTarget;
import com.slack.bot.application.interactivity.box.aop.ResolveOutboxSource;
import com.slack.bot.application.interactivity.box.aop.TriggerInteractivityImmediateProcessing;
import com.slack.bot.application.interactivity.box.out.exception.SlackBlocksSerializationException;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackNotificationOutboxWriter {

    private final ObjectMapper objectMapper;
    private final SlackNotificationOutboxRepository slackNotificationOutboxRepository;
    private final SlackInteractionIdempotencyKeyGenerator idempotencyKeyGenerator;

    @ResolveOutboxSource
    @TriggerInteractivityImmediateProcessing(InteractivityImmediateTriggerTarget.OUTBOX)
    public void enqueueEphemeralText(String sourceKey, String teamId, String channelId, String userId, String text) {
        enqueue(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_TEXT,
                teamId,
                channelId,
                userId,
                text,
                null,
                null
        );
    }

    @ResolveOutboxSource
    @TriggerInteractivityImmediateProcessing(InteractivityImmediateTriggerTarget.OUTBOX)
    public void enqueueEphemeralBlocks(
            String sourceKey,
            String teamId,
            String channelId,
            String userId,
            Object blocks,
            String fallbackText
    ) {
        enqueue(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS,
                teamId,
                channelId,
                userId,
                null,
                serializeBlocks(blocks),
                fallbackText
        );
    }

    @ResolveOutboxSource
    @TriggerInteractivityImmediateProcessing(InteractivityImmediateTriggerTarget.OUTBOX)
    public void enqueueChannelText(String sourceKey, String teamId, String channelId, String text) {
        enqueue(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                teamId,
                channelId,
                null,
                text,
                null,
                null
        );
    }

    @ResolveOutboxSource
    @TriggerInteractivityImmediateProcessing(InteractivityImmediateTriggerTarget.OUTBOX)
    public void enqueueChannelBlocks(String sourceKey, String teamId, String channelId, Object blocks,
            String fallbackText) {
        enqueue(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                teamId,
                channelId,
                null,
                null,
                serializeBlocks(blocks),
                fallbackText
        );
    }

    private String serializeBlocks(Object blocks) {
        if (blocks == null) {
            return null;
        }

        if (blocks instanceof String blocksJson) {
            return normalizeBlocksJson(blocksJson);
        }

        return objectMapper.valueToTree(blocks).toString();
    }

    private String normalizeBlocksJson(String blocksJson) {
        try {
            return objectMapper.readTree(blocksJson).toString();
        } catch (JsonProcessingException exception) {
            throw new SlackBlocksSerializationException("blocks JSON 직렬화에 실패했습니다.", exception);
        }
    }

    private void enqueue(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId,
            String text,
            String blocksJson,
            String fallbackText
    ) {
        String key = idempotencyKey(sourceKey, messageType, teamId, channelId, userId);
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(messageType)
                                                                .idempotencyKey(key)
                                                                .teamId(teamId)
                                                                .channelId(channelId)
                                                                .userId(userId)
                                                                .text(text)
                                                                .blocksJson(blocksJson)
                                                                .fallbackText(fallbackText)
                                                                .build();

        slackNotificationOutboxRepository.enqueue(outbox);
    }

    private String idempotencyKey(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId
    ) {
        ObjectNode source = objectMapper.createObjectNode();

        source.put("source", sourceKey);
        source.put("messageType", messageType.name());
        source.put("target", targetKey(teamId, channelId, userId));

        return idempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.SLACK_NOTIFICATION_OUTBOX,
                source.toString()
        );
    }

    private String targetKey(String teamId, String channelId, String userId) {
        return nullToEmpty(teamId) + ":" + nullToEmpty(channelId) + ":" + nullToEmpty(userId);
    }

    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }
}
