package com.slack.bot.application.interactivity.client;

import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxWriter;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationApiClient {

    private static final String AD_HOC_SOURCE_PREFIX = "BUSINESS:ADHOC:";

    private final SlackNotificationOutboxWriter slackNotificationOutboxWriter;
    private final NotificationTransportApiClient notificationTransportApiClient;
    private final OutboxIdempotencySourceContext outboxIdempotencySourceContext;
    private final WorkspaceAccessTokenTeamIdResolver workspaceAccessTokenTeamIdResolver;

    public void sendEphemeralMessage(String token, String channelId, String targetUserId, String text) {
        sendEphemeralMessage(null, token, channelId, targetUserId, text);
    }

    public void sendEphemeralMessage(
            String sourceKey,
            String token,
            String channelId,
            String targetUserId,
            String text
    ) {
        String teamId = workspaceAccessTokenTeamIdResolver.resolve(token);

        slackNotificationOutboxWriter.enqueueEphemeralText(
                resolveSourceKey(sourceKey),
                teamId,
                channelId,
                targetUserId,
                text
        );
    }

    public void sendEphemeralBlockMessage(
            String token,
            String channelId,
            String targetUserId,
            Object blocks,
            String fallbackText
    ) {
        sendEphemeralBlockMessage(null, token, channelId, targetUserId, blocks, fallbackText);
    }

    public void sendEphemeralBlockMessage(
            String sourceKey,
            String token,
            String channelId,
            String targetUserId,
            Object blocks,
            String fallbackText
    ) {
        String teamId = workspaceAccessTokenTeamIdResolver.resolve(token);

        slackNotificationOutboxWriter.enqueueEphemeralBlocks(
                resolveSourceKey(sourceKey),
                teamId,
                channelId,
                targetUserId,
                blocks,
                fallbackText
        );
    }

    public void sendMessage(String token, String channelId, String text) {
        sendMessage(null, token, channelId, text);
    }

    public void sendMessage(String sourceKey, String token, String channelId, String text) {
        String teamId = workspaceAccessTokenTeamIdResolver.resolve(token);

        slackNotificationOutboxWriter.enqueueChannelText(
                resolveSourceKey(sourceKey),
                teamId,
                channelId,
                text
        );
    }

    public void sendBlockMessage(String token, String channelId, Object blocks, String fallbackText) {
        sendBlockMessage(null, token, channelId, blocks, fallbackText);
    }

    public void sendBlockMessage(
            String sourceKey,
            String token,
            String channelId,
            Object blocks,
            String fallbackText
    ) {
        String teamId = workspaceAccessTokenTeamIdResolver.resolve(token);

        slackNotificationOutboxWriter.enqueueChannelBlocks(
                resolveSourceKey(sourceKey),
                teamId,
                channelId,
                blocks,
                fallbackText
        );
    }

    public void openModal(String token, String triggerId, Object view) {
        notificationTransportApiClient.openModal(token, triggerId, view);
    }

    public String openDirectMessageChannel(String token, String userId) {
        return notificationTransportApiClient.openDirectMessageChannel(token, userId);
    }

    public <T> T withBusinessEventSource(String businessEventId, Supplier<T> supplier) {
        return outboxIdempotencySourceContext.withBusinessEventSource(businessEventId, supplier);
    }

    private String resolveSourceKey(String sourceKey) {
        if (sourceKey != null) {
            return sourceKey;
        }

        String contextSourceKey = outboxIdempotencySourceContext.currentSourceKey()
                                                                .orElse(null);
        if (contextSourceKey != null) {
            return contextSourceKey;
        }

        return AD_HOC_SOURCE_PREFIX + UUID.randomUUID();
    }

}
