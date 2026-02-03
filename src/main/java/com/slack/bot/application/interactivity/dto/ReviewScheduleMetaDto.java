package com.slack.bot.application.interactivity.dto;

import lombok.Builder;

@Builder
public record ReviewScheduleMetaDto(
        String teamId,
        String channelId,
        Long pullRequestId,
        int pullRequestNumber,
        String pullRequestTitle,
        String pullRequestUrl,
        String authorGithubId,
        String authorSlackId,
        String reservationId,
        String projectId
) { }
