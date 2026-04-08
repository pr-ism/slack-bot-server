package com.slack.bot.application.interaction.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyScope;
import com.slack.bot.application.interaction.box.aop.InteractionImmediateTriggerTarget;
import com.slack.bot.application.interaction.box.aop.ResolveOutboxSource;
import com.slack.bot.application.interaction.box.aop.TriggerInteractionImmediateProcessing;
import com.slack.bot.application.interaction.box.out.exception.SlackBlocksSerializationException;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStringField;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackNotificationOutboxWriter {

    private final ObjectMapper objectMapper;
    private final OutboxIdempotencyPayloadEncoder outboxIdempotencyPayloadEncoder;
    private final SlackNotificationOutboxRepository slackNotificationOutboxRepository;
    private final SlackInteractionIdempotencyKeyGenerator idempotencyKeyGenerator;

    @ResolveOutboxSource
    @TriggerInteractionImmediateProcessing(InteractionImmediateTriggerTarget.OUTBOX)
    public void enqueueEphemeralText(String sourceKey, String teamId, String channelId, String userId, String text) {
        enqueue(OutboxEnqueueRequest.ephemeralText(sourceKey, teamId, channelId, userId, validateText(text)));
    }

    @ResolveOutboxSource
    @TriggerInteractionImmediateProcessing(InteractionImmediateTriggerTarget.OUTBOX)
    public void enqueueEphemeralBlocks(
            String sourceKey,
            String teamId,
            String channelId,
            String userId,
            JsonNode blocks,
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
    @TriggerInteractionImmediateProcessing(InteractionImmediateTriggerTarget.OUTBOX)
    public void enqueueEphemeralBlocks(
            String sourceKey,
            String teamId,
            String channelId,
            String userId,
            String blocksJson,
            String fallbackText
    ) {
        enqueue(OutboxEnqueueRequest.ephemeralBlocks(
                sourceKey,
                teamId,
                channelId,
                userId,
                normalizeBlocksJson(blocksJson),
                fallbackText
        ));
    }

    @ResolveOutboxSource
    @TriggerInteractionImmediateProcessing(InteractionImmediateTriggerTarget.OUTBOX)
    public void enqueueChannelText(String sourceKey, String teamId, String channelId, String text) {
        enqueue(OutboxEnqueueRequest.channelText(sourceKey, teamId, channelId, validateText(text)));
    }

    @ResolveOutboxSource
    @TriggerInteractionImmediateProcessing(InteractionImmediateTriggerTarget.OUTBOX)
    public void enqueueChannelBlocks(
            String sourceKey,
            String teamId,
            String channelId,
            JsonNode blocks,
            String fallbackText
    ) {
        enqueue(OutboxEnqueueRequest.channelBlocks(
                sourceKey,
                teamId,
                channelId,
                serializeBlocks(blocks),
                fallbackText
        ));
    }

    @ResolveOutboxSource
    @TriggerInteractionImmediateProcessing(InteractionImmediateTriggerTarget.OUTBOX)
    public void enqueueChannelBlocks(
            String sourceKey,
            String teamId,
            String channelId,
            String blocksJson,
            String fallbackText
    ) {
        enqueue(OutboxEnqueueRequest.channelBlocks(
                sourceKey,
                teamId,
                channelId,
                normalizeBlocksJson(blocksJson),
                fallbackText
        ));
    }

    private String serializeBlocks(JsonNode blocks) {
        if (blocks == null) {
            throw new IllegalArgumentException("blocks는 null일 수 없습니다.");
        }

        return blocks.toString();
    }

    private String validateText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text는 null일 수 없습니다.");
        }

        return text;
    }

    private String normalizeBlocksJson(String blocksJson) {
        if (blocksJson == null) {
            throw new IllegalArgumentException("blocks는 null일 수 없습니다.");
        }

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
                request.userId().valueOrBlank()
        );
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(request.messageType())
                                                                .idempotencyKey(key)
                                                                .teamId(request.teamId())
                                                                .channelId(request.channelId())
                                                                .userIdField(request.userId())
                                                                .textField(request.text())
                                                                .blocksJsonField(request.blocksJson())
                                                                .fallbackTextField(request.fallbackText())
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
        String sourcePayload = outboxIdempotencyPayloadEncoder.encode(
                sourceKey,
                messageType,
                teamId,
                channelId,
                userId
        );

        return idempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.SLACK_NOTIFICATION_OUTBOX,
                sourcePayload
        );
    }

    private record OutboxEnqueueRequest(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            SlackNotificationOutboxStringField userId,
            SlackNotificationOutboxStringField text,
            SlackNotificationOutboxStringField blocksJson,
            SlackNotificationOutboxStringField fallbackText
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
                    SlackNotificationOutboxStringField.present(userId),
                    SlackNotificationOutboxStringField.present(text),
                    SlackNotificationOutboxStringField.absent(),
                    SlackNotificationOutboxStringField.absent()
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
                    SlackNotificationOutboxStringField.present(userId),
                    SlackNotificationOutboxStringField.absent(),
                    SlackNotificationOutboxStringField.present(blocksJson),
                    fallbackTextField(fallbackText)
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
                    SlackNotificationOutboxStringField.absent(),
                    SlackNotificationOutboxStringField.present(text),
                    SlackNotificationOutboxStringField.absent(),
                    SlackNotificationOutboxStringField.absent()
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
                    SlackNotificationOutboxStringField.absent(),
                    SlackNotificationOutboxStringField.absent(),
                    SlackNotificationOutboxStringField.present(blocksJson),
                    fallbackTextField(fallbackText)
            );
        }

        private static SlackNotificationOutboxStringField fallbackTextField(String fallbackText) {
            if (fallbackText == null || fallbackText.isBlank()) {
                return SlackNotificationOutboxStringField.absent();
            }

            return SlackNotificationOutboxStringField.present(fallbackText);
        }
    }
}
