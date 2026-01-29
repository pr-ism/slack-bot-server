package com.slack.bot.application.event.parser.dto;

import lombok.Builder;

@Builder
public record MemberJoinedEventPayload(
        String teamId,
        String joinedUserId,
        String channelId,
        String inviterId
) {
}
