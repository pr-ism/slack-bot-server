package com.slack.bot.application.review.participant.dto;

import java.util.List;

public record ReviewParticipantsDto(
        String authorText,
        String pendingReviewersText,
        List<String> unmappedGithubIds
) {
}
