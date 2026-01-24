package com.slack.bot.domain.member;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.domain.member.vo.GithubId;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "project_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember extends BaseTimeEntity {

    private String teamId;
    private String slackUserId;
    private String displayName;

    @Embedded
    private GithubId githubId;

    @Builder
    private ProjectMember(String teamId, String slackUserId, String displayName) {
        validateTeamId(teamId);
        validateSlackUserId(slackUserId);
        validateDisplayName(displayName);

        this.teamId = teamId;
        this.slackUserId = slackUserId;
        this.displayName = displayName;
        this.githubId = GithubId.EMPTY;
    }

    private static void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("팀 ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validateSlackUserId(String slackUserId) {
        if (slackUserId == null || slackUserId.isBlank()) {
            throw new IllegalArgumentException("슬랙 사용자 ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("표시 이름은 비어 있을 수 없습니다.");
        }
    }

    public void connectGithubId(GithubId githubId) {
        validateGithubId(githubId);
        this.githubId = githubId;
    }

    private void validateGithubId(GithubId githubId) {
        if (githubId == null || githubId == GithubId.EMPTY) {
            throw new IllegalArgumentException("GitHub ID는 비어 있을 수 없습니다.");
        }
    }
}
