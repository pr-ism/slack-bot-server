package com.slack.bot.application.interaction.reply;

import com.slack.bot.application.interaction.client.NotificationApiClient;
import com.slack.bot.application.interaction.reply.dto.response.SlackActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackActionErrorNotifier {

    private final NotificationApiClient notificationApiClient;

    public void notify(String token, String channelId, String slackUserId, InteractionErrorType type) {
        notificationApiClient.sendEphemeralMessage(token, channelId, slackUserId, type.message());
    }

    public SlackActionResponse respond(String token, String channelId, String slackUserId, InteractionErrorType type) {
        notify(token, channelId, slackUserId, type);

        return SlackActionResponse.empty();
    }
}
