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
        enqueue(OutboxEnqueueRequest.ephemeralText(sourceKey, teamId, channelId, userId, text));
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
        enqueue(OutboxEnqueueRequest.ephemeralBlocks(
                sourceKey,
                teamId,
                channelId,
                userId,
                serializeBlocks(blocks),
                fallbackText
        ));
    }

    @ResolveOutboxSource
    @TriggerInteractivityImmediateProcessing(InteractivityImmediateTriggerTarget.OUTBOX)
    public void enqueueChannelText(String sourceKey, String teamId, String channelId, String text) {
        enqueue(OutboxEnqueueRequest.channelText(sourceKey, teamId, channelId, text));
    }

    @ResolveOutboxSource
    @TriggerInteractivityImmediateProcessing(InteractivityImmediateTriggerTarget.OUTBOX)
    public void enqueueChannelBlocks(String sourceKey, String teamId, String channelId, Object blocks,
            String fallbackText) {
        enqueue(OutboxEnqueueRequest.channelBlocks(
                sourceKey,
                teamId,
                channelId,
                serializeBlocks(blocks),
                fallbackText
        ));
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

    private void enqueue(OutboxEnqueueRequest request) {
        String key = idempotencyKey(
                request.sourceKey(),
                request.messageType(),
                request.teamId(),
                request.channelId(),
                request.userId()
        );
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(request.messageType())
                                                                .idempotencyKey(key)
                                                                .teamId(request.teamId())
                                                                .channelId(request.channelId())
                                                                .userId(request.userId())
                                                                .text(request.text())
                                                                .blocksJson(request.blocksJson())
                                                                .fallbackText(request.fallbackText())
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

    private record OutboxEnqueueRequest(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId,
            String text,
            String blocksJson,
            String fallbackText
    ) {
        private static OutboxEnqueueRequest ephemeralText(
                String sourceKey,
                String teamId,
                String channelId,
                String userId,
                String text
        ) {
            return new OutboxEnqueueRequest(
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

        private static OutboxEnqueueRequest ephemeralBlocks(
                String sourceKey,
                String teamId,
                String channelId,
                String userId,
                String blocksJson,
                String fallbackText
        ) {
            return new OutboxEnqueueRequest(
                    sourceKey,
                    SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS,
                    teamId,
                    channelId,
                    userId,
                    null,
                    blocksJson,
                    fallbackText
            );
        }

        private static OutboxEnqueueRequest channelText(
                String sourceKey,
                String teamId,
                String channelId,
                String text
        ) {
            return new OutboxEnqueueRequest(
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

        private static OutboxEnqueueRequest channelBlocks(
                String sourceKey,
                String teamId,
                String channelId,
                String blocksJson,
                String fallbackText
        ) {
            return new OutboxEnqueueRequest(
                    sourceKey,
                    SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                    teamId,
                    channelId,
                    null,
                    null,
                    blocksJson,
                    fallbackText
            );
        }
    }
}
