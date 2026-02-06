package com.slack.bot.application.interactivity.reply;

import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackActionErrorNotifier {

    private final NotificationApiClient notificationApiClient;

    public void notify(String token, String channelId, String slackUserId, InteractivityErrorType type) {
        notificationApiClient.sendEphemeralMessage(token, channelId, slackUserId, type.message());
    }

    public Object respond(String token, String channelId, String slackUserId, InteractivityErrorType type) {
        notify(token, channelId, slackUserId, type);

        return SlackActionResponse.empty();
    }
}
