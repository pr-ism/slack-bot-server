package com.slack.bot.domain.workspace;

import com.slack.bot.domain.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseTimeEntity {

    private Long userId;
    private String teamId;
    private String accessToken;
    private String botUserId;

    @Builder
    private Workspace(String teamId, String accessToken, String botUserId, Long userId) {
        validateTeamId(teamId);
        validateAccessToken(accessToken);
        validateBotUserId(botUserId);
        validateUserId(userId);

        this.teamId = teamId;
        this.accessToken = accessToken;
        this.botUserId = botUserId;
        this.userId = userId;
    }

    private void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("슬랙 봇의 team ID는 비어 있을 수 없습니다.");
        }
    }

    private void validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("슬랙 봇의 access token은 비어 있을 수 없습니다.");
        }
    }

    private void validateBotUserId(String botUserId) {
        if (botUserId == null || botUserId.isBlank()) {
            throw new IllegalArgumentException("슬랙 봇의 user ID는 비어 있을 수 없습니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("워크스페이스 생성 회원 ID는 비어 있을 수 없습니다.");
        }
    }

    public void reconnect(String accessToken) {
        validateAccessToken(accessToken);

        this.accessToken = accessToken;
    }
}
