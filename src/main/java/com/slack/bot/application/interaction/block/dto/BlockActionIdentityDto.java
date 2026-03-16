package com.slack.bot.application.interaction.block.dto;

public record BlockActionIdentityDto(boolean valid, String teamId, String channelId, String slackUserId) {

    public static BlockActionIdentityDto valid(String teamId, String channelId, String slackUserId) {
        return new BlockActionIdentityDto(true, teamId, channelId, slackUserId);
    }

    public static BlockActionIdentityDto empty() {
        return new BlockActionIdentityDto(false, "", "", "");
    }
}
