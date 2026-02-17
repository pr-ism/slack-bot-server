package com.slack.bot.application.interactivity.client;

import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxWriter;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationApiClient {

    private final SlackNotificationOutboxWriter slackNotificationOutboxWriter;
    private final NotificationTransportApiClient notificationTransportApiClient;
    private final OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    public void sendEphemeralMessage(String token, String channelId, String targetUserId, String text) {
        slackNotificationOutboxWriter.enqueueEphemeralText(
                null,
                token,
                channelId,
                targetUserId,
                text
        );
    }

    public void sendEphemeralBlockMessage(String token, String channelId, String targetUserId, Object blocks, String text) {
        slackNotificationOutboxWriter.enqueueEphemeralBlocks(
                null,
                token,
                channelId,
                targetUserId,
                blocks,
                text
        );
    }

    public void sendMessage(String token, String channelId, String text) {
        slackNotificationOutboxWriter.enqueueChannelText(
                null,
                token,
                channelId,
                text
        );
    }

    public void sendBlockMessage(String token, String channelId, Object blocks, String text) {
        slackNotificationOutboxWriter.enqueueChannelBlocks(
                null,
                token,
                channelId,
                blocks,
                text
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
}
