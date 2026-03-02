package com.slack.bot.domain.round;

import com.slack.bot.domain.common.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "round_reviewer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoundReviewer extends CreatedAtEntity {

    private Long pullRequestRoundId;

    private String reviewerGithubId;

    @Enumerated(EnumType.STRING)
    private RoundReviewerState state;

    public static RoundReviewer requested(Long pullRequestRoundId, String reviewerGithubId) {
        validatePullRequestRoundId(pullRequestRoundId);
        validateReviewerGithubId(reviewerGithubId);

        return new RoundReviewer(pullRequestRoundId, reviewerGithubId, RoundReviewerState.REQUESTED);
    }

    private static void validatePullRequestRoundId(Long pullRequestRoundId) {
        if (pullRequestRoundId == null || pullRequestRoundId <= 0) {
            throw new IllegalArgumentException("pullRequestRoundId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateReviewerGithubId(String reviewerGithubId) {
        if (reviewerGithubId == null || reviewerGithubId.isBlank()) {
            throw new IllegalArgumentException("reviewerGithubId는 비어 있을 수 없습니다.");
        }
    }

    private RoundReviewer(Long pullRequestRoundId, String reviewerGithubId, RoundReviewerState state) {
        this.pullRequestRoundId = pullRequestRoundId;
        this.reviewerGithubId = reviewerGithubId;
        this.state = state;
    }

    public boolean isRequested() {
        return state.isRequested();
    }

    public void markRequested() {
        state = state.request();
    }

    public void markReviewed() {
        state = state.review();
    }
}
