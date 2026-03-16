package com.slack.bot.application.interaction.block.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interaction.block.BlockActionType;
import lombok.Builder;

@Builder
public record BlockActionCommandDto(
        JsonNode payload,
        JsonNode action,
        String actionId,
        BlockActionType actionType,
        String teamId,
        String channelId,
        String slackUserId,
        String botToken
) {

    @Override
    public String toString() {
        return "BlockActionCommandDto[" +
                "actionId=" + actionId +
                ", actionType=" + actionType +
                ", teamId=" + teamId +
                ", channelId=" + channelId +
                ", slackUserId=" + slackUserId +
                ", botToken=***]";
    }
}
