package com.slack.bot.application.interactivity.view.dto;

import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;

public record ViewSubmissionImmediateDto(
        SlackActionResponse response,
        boolean shouldEnqueue
) {

    public static ViewSubmissionImmediateDto enqueue(SlackActionResponse response) {
        return new ViewSubmissionImmediateDto(response, true);
    }

    public static ViewSubmissionImmediateDto noEnqueue(SlackActionResponse response) {
        return new ViewSubmissionImmediateDto(response, false);
    }
}
