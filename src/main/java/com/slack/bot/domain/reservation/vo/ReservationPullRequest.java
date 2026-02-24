package com.slack.bot.domain.reservation.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationPullRequest {

    private Long githubPullRequestId;
    private int pullRequestNumber;
    private String pullRequestTitle;
    private String pullRequestUrl;

    @Builder
    private ReservationPullRequest(
            Long githubPullRequestId,
            int pullRequestNumber,
            String pullRequestTitle,
            String pullRequestUrl
    ) {
        validateGithubPullRequestId(githubPullRequestId);
        validatePullRequestNumber(pullRequestNumber);
        validatePullRequestTitle(pullRequestTitle);
        validatePullRequestUrl(pullRequestUrl);

        this.githubPullRequestId = githubPullRequestId;
        this.pullRequestNumber = pullRequestNumber;
        this.pullRequestTitle = pullRequestTitle;
        this.pullRequestUrl = pullRequestUrl;
    }

    private static void validateGithubPullRequestId(Long githubPullRequestId) {
        if (githubPullRequestId == null || githubPullRequestId <= 0) {
            throw new IllegalArgumentException("Pull Request ID는 0보다 커야 합니다.");
        }
    }

    private static void validatePullRequestNumber(int pullRequestNumber) {
        if (pullRequestNumber <= 0) {
            throw new IllegalArgumentException("Pull Request 번호는 0보다 커야 합니다.");
        }
    }

    private static void validatePullRequestTitle(String pullRequestTitle) {
        if (pullRequestTitle == null || pullRequestTitle.isBlank()) {
            throw new IllegalArgumentException("Pull Request 제목은 비어 있을 수 없습니다.");
        }
    }

    private static void validatePullRequestUrl(String pullRequestUrl) {
        if (pullRequestUrl == null || pullRequestUrl.isBlank()) {
            throw new IllegalArgumentException("Pull Request URL은 비어 있을 수 없습니다.");
        }
    }
}
