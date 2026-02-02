package com.slack.bot.application.review.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ReviewRequestEventRequest(

        @NotBlank(message = "repositoryName은 필수입니다.")
        String repositoryName,

        @NotBlank(message = "pullRequestId는 필수입니다.")
        String pullRequestId,

        @Positive(message = "pullRequestNumber는 1 이상이어야 합니다.")
        int pullRequestNumber,

        @NotBlank(message = "pullRequestTitle은 필수입니다.")
        String pullRequestTitle,

        @NotBlank(message = "pullRequestUrl은 필수입니다.")
        String pullRequestUrl,

        @NotBlank(message = "authorGithubId는 필수입니다.")
        String authorGithubId,

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> pendingReviewers,

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> reviewedReviewers
) {
}
