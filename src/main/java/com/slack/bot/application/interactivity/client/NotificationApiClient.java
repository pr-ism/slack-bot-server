package com.slack.bot.application.interactivity.client;

import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxWriter;
import com.slack.bot.application.interactivity.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
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
    private final WorkspaceRepository workspaceRepository;

    public void sendEphemeralMessage(String token, String channelId, String targetUserId, String text) {
        String teamId = resolveTeamId(token);

        slackNotificationOutboxWriter.enqueueEphemeralText(
                null,
                teamId,
                channelId,
                targetUserId,
                text
        );
    }

    public void sendEphemeralBlockMessage(String token, String channelId, String targetUserId, Object blocks, String text) {
        String teamId = resolveTeamId(token);

        slackNotificationOutboxWriter.enqueueEphemeralBlocks(
                null,
                teamId,
                channelId,
                targetUserId,
                blocks,
                text
        );
    }

    public void sendMessage(String token, String channelId, String text) {
        String teamId = resolveTeamId(token);

        slackNotificationOutboxWriter.enqueueChannelText(
                null,
                teamId,
                channelId,
                text
        );
    }

    public void sendBlockMessage(String token, String channelId, Object blocks, String text) {
        String teamId = resolveTeamId(token);

        slackNotificationOutboxWriter.enqueueChannelBlocks(
                null,
                teamId,
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

    private String resolveTeamId(String token) {
        Workspace workspace = workspaceRepository.findByAccessToken(token)
                                                 .orElseThrow(() -> OutboxWorkspaceNotFoundException.forToken(token));

        return workspace.getTeamId();
    }
}
