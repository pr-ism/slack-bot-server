package com.slack.bot.application.interactivity.box.out;

import com.slack.bot.application.interactivity.box.out.exception.OutboxMessageTypeRequiredException;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import org.springframework.stereotype.Component;

@Component
public class OutboxIdempotencyPayloadEncoder {

    public String encode(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId
    ) {
        if (messageType == null) {
            throw new OutboxMessageTypeRequiredException();
        }

        String messageTypeName = messageType.name();

        return "source=" + encodeComponent(sourceKey)
                + "|messageType=" + encodeComponent(messageTypeName)
                + "|teamId=" + encodeComponent(teamId)
                + "|channelId=" + encodeComponent(channelId)
                + "|userId=" + encodeComponent(userId);
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
