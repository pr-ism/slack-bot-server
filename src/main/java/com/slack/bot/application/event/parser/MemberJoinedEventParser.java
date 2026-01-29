package com.slack.bot.application.event.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.parser.dto.MemberJoinedEventPayload;
import org.springframework.stereotype.Component;

@Component
public class MemberJoinedEventParser {

    public MemberJoinedEventPayload parse(JsonNode payload) {
        JsonNode event = payload.path("event");
        String teamId = payload.path("team_id").asText();
        String joinedUserId = event.path("user").asText();
        String channelId = event.path("channel").asText();
        String channelName = extractText(event, "channel_name");
        String inviterId = extractText(event, "inviter");

        return MemberJoinedEventPayload.builder()
                                       .teamId(teamId)
                                       .joinedUserId(joinedUserId)
                                       .channelId(channelId)
                                       .channelName(channelName)
                                       .inviterId(inviterId)
                                       .build();
    }

    private String extractText(JsonNode node, String fieldName) {
        JsonNode target = node.get(fieldName);

        if (target == null || target.isNull()) {
            return null;
        }

        return target.asText();
    }
}
