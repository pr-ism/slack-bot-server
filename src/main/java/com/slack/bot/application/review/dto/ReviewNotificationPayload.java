package com.slack.bot.application.review.dto;

import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record ReviewNotificationPayload(
        String repositoryName,
        Long githubPullRequestId,
        int pullRequestNumber,
        String pullRequestTitle,
        String pullRequestUrl,
        String authorGithubId,
        List<String> pendingReviewers,
        List<String> reviewersToMention,
        String reviewRoundKey
) {

    public static ReviewNotificationPayload of(ReviewAssignmentRequest request, List<String> reviewersToMention) {
        return of(request, reviewersToMention, null);
    }

    public static ReviewNotificationPayload of(
            ReviewAssignmentRequest request,
            List<String> reviewersToMention,
            String reviewRoundKey
    ) {
        return new ReviewNotificationPayload(
                request.repositoryName(),
                request.githubPullRequestId(),
                request.pullRequestNumber(),
                request.pullRequestTitle(),
                request.pullRequestUrl(),
                request.authorGithubId(),
                mergePendingReviewers(request.pendingReviewers(), reviewersToMention),
                reviewersToMention,
                reviewRoundKey
        );
    }

    public ReviewNotificationPayload(
            String repositoryName,
            Long githubPullRequestId,
            int pullRequestNumber,
            String pullRequestTitle,
            String pullRequestUrl,
            String authorGithubId,
            List<String> pendingReviewers,
            List<String> reviewersToMention
    ) {
        this(
                repositoryName,
                githubPullRequestId,
                pullRequestNumber,
                pullRequestTitle,
                pullRequestUrl,
                authorGithubId,
                pendingReviewers,
                reviewersToMention,
                null
        );
    }

    private static List<String> mergePendingReviewers(List<String> pendingReviewers, List<String> reviewersToMention) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();

        if (pendingReviewers != null) {
            merged.addAll(pendingReviewers);
        }
        if (reviewersToMention != null) {
            merged.addAll(reviewersToMention);
        }

        return new ArrayList<>(merged);
    }
}
