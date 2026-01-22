package com.slack.bot.domain.workspace;

import com.slack.bot.domain.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseTimeEntity {

    private String teamId;
    private String accessToken;

    public static Workspace create(String teamId, String accessToken) {
        validateTeamId(teamId);
        validateAccessToken(accessToken);

        return new Workspace(teamId, accessToken);
    }

    private static void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("슬랙 봇의 team ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("슬랙 봇의 access token은 비어 있을 수 없습니다.");
        }
    }

    private Workspace(String teamId, String accessToken) {
        this.teamId = teamId;
        this.accessToken = accessToken;
    }

    public void reconnect(String accessToken) {
        validateAccessToken(accessToken);

        this.accessToken = accessToken;
    }
}
