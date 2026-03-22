package com.slack.bot.domain.round;

import com.slack.bot.domain.common.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "pull_request_rounds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestRound extends CreatedAtEntity {

    private String apiKey;

    private Long githubPullRequestId;

    private int roundNumber;

    private String startCommitHash;

    public static PullRequestRound create(
            String apiKey,
            Long githubPullRequestId,
            int roundNumber,
            String startCommitHash
    ) {
        validateApiKey(apiKey);
        validateGithubPullRequestId(githubPullRequestId);
        validateRoundNumber(roundNumber);
        validateStartCommitHash(startCommitHash);

        return new PullRequestRound(apiKey, githubPullRequestId, roundNumber, startCommitHash);
    }

    private static void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey는 비어 있을 수 없습니다.");
        }
    }

    private static void validateGithubPullRequestId(Long githubPullRequestId) {
        if (githubPullRequestId == null || githubPullRequestId <= 0) {
            throw new IllegalArgumentException("githubPullRequestId는 비어 있을 수 없습니다.");
        }
    }

    private static void validateRoundNumber(int roundNumber) {
        if (roundNumber <= 0) {
            throw new IllegalArgumentException("roundNumber는 1 이상이어야 합니다.");
        }
    }

    private static void validateStartCommitHash(String startCommitHash) {
        if (startCommitHash == null || startCommitHash.isBlank()) {
            throw new IllegalArgumentException("startCommitHash는 비어 있을 수 없습니다.");
        }
    }

    private PullRequestRound(
            String apiKey,
            Long githubPullRequestId,
            int roundNumber,
            String startCommitHash
    ) {
        this.apiKey = apiKey;
        this.githubPullRequestId = githubPullRequestId;
        this.roundNumber = roundNumber;
        this.startCommitHash = startCommitHash;
    }

    public boolean hasSameStartCommitHash(String startCommitHash) {
        validateStartCommitHash(startCommitHash);

        return this.startCommitHash.equals(startCommitHash);
    }

    public String batchKey() {
        return apiKey + ":" + githubPullRequestId + ":" + roundNumber;
    }
}
