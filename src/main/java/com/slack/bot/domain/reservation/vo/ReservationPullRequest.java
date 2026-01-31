package com.slack.bot.domain.reservation.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationPullRequest {

    private String pullRequestId;
    private String pullRequestNumber;
    private String pullRequestTitle;
    private String pullRequestUrl;

    private ReservationPullRequest(
            String pullRequestId,
            String pullRequestNumber,
            String pullRequestTitle,
            String pullRequestUrl
    ) {
        validatePullRequestId(pullRequestId);
        validatePullRequestNumber(pullRequestNumber);
        validatePullRequestTitle(pullRequestTitle);
        validatePullRequestUrl(pullRequestUrl);

        this.pullRequestId = pullRequestId;
        this.pullRequestNumber = pullRequestNumber;
        this.pullRequestTitle = pullRequestTitle;
        this.pullRequestUrl = pullRequestUrl;
    }

    public static ReservationPullRequest of(
            String pullRequestId,
            String pullRequestNumber,
            String pullRequestTitle,
            String pullRequestUrl
    ) {
        return new ReservationPullRequest(pullRequestId, pullRequestNumber, pullRequestTitle, pullRequestUrl);
    }

    private static void validatePullRequestId(String pullRequestId) {
        if (pullRequestId == null || pullRequestId.isBlank()) {
            throw new IllegalArgumentException("Pull Request ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validatePullRequestNumber(String pullRequestNumber) {
        if (pullRequestNumber == null || pullRequestNumber.isBlank()) {
            throw new IllegalArgumentException("Pull Request 번호는 비어 있을 수 없습니다.");
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
