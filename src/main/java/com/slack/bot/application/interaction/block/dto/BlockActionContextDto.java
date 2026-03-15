package com.slack.bot.application.interaction.block.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder
public record BlockActionContextDto(
        String teamId,
        String channelId,
        String slackUserId,
        String actionId,
        JsonNode action,
        String botToken
) {
}
