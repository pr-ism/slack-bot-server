package com.slack.bot.application.review.channel.dto;

public record SlackChannelDto(Long projectId, String teamId, String channelId, String accessToken) {
}
