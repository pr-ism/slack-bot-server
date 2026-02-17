package com.slack.bot.application.interactivity.view.dto;

import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;

public record ViewSubmissionSyncResultDto(
        SlackActionResponse response,
        boolean shouldEnqueue
) {

    public static ViewSubmissionSyncResultDto enqueue(SlackActionResponse response) {
        return new ViewSubmissionSyncResultDto(response, true);
    }

    public static ViewSubmissionSyncResultDto noEnqueue(SlackActionResponse response) {
        return new ViewSubmissionSyncResultDto(response, false);
    }
}
