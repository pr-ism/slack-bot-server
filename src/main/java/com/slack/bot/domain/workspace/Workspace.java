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

    private String accessToken;
    private String installedBy;

    public static Workspace create(String accessToken, String installedBy) {
        validateAccessToken(accessToken);
        validateInstalledBy(installedBy);

        return new Workspace(accessToken, installedBy);
    }

    private static void validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("슬랙 봇의 access token은 비어 있을 수 없습니다.");
        }
    }

    private static void validateInstalledBy(String installedBy) {
        if (installedBy == null || installedBy.isBlank()) {
            throw new IllegalArgumentException("슬랙 봇을 설치한 회원은 비어 있을 수 없습니다.");
        }
    }

    private Workspace(String accessToken, String installedBy) {
        this.accessToken = accessToken;
        this.installedBy = installedBy;
    }
}
