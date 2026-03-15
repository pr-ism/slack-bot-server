package com.slack.bot.application.interaction.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SchedulerContextDto(
        JsonNode payload,
        JsonNode action,
        String teamId,
        String channelId,
        String slackUserId,
        String token,
        String triggerId,
        String metaJson
) {
}
