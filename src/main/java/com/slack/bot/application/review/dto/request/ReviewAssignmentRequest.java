package com.slack.bot.application.review.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ReviewAssignmentRequest(

        @NotBlank(message = "repositoryName은 필수입니다.")
        String repositoryName,

        @NotNull(message = "githubPullRequestId는 필수입니다.")
        @Positive(message = "githubPullRequestId는 1 이상이어야 합니다.")
        Long githubPullRequestId,

        @Positive(message = "pullRequestNumber는 1 이상이어야 합니다.")
        int pullRequestNumber,

        @NotBlank(message = "pullRequestTitle은 필수입니다.")
        String pullRequestTitle,

        @NotBlank(message = "pullRequestUrl은 필수입니다.")
        String pullRequestUrl,

        @NotBlank(message = "authorGithubId는 필수입니다.")
        String authorGithubId,

        @NotBlank(message = "startCommitHash는 필수입니다.")
        String startCommitHash,

        @NotEmpty(message = "pendingReviewers는 1명 이상이어야 합니다.")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> pendingReviewers,

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> reviewedReviewers
) {

        public ReviewAssignmentRequest(
                String repositoryName,
                Long githubPullRequestId,
                int pullRequestNumber,
                String pullRequestTitle,
                String pullRequestUrl,
                String authorGithubId,
                List<String> pendingReviewers,
                List<String> reviewedReviewers
        ) {
                this(
                        repositoryName,
                        githubPullRequestId,
                        pullRequestNumber,
                        pullRequestTitle,
                        pullRequestUrl,
                        authorGithubId,
                        "unknown-start-commit",
                        pendingReviewers,
                        reviewedReviewers
                );
        }
}
