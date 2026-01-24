package com.slack.bot.domain.member.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubId {

    public static final GithubId EMPTY = new GithubId(null);

    @Column(name = "github_id")
    private String value;

    private GithubId(String value) {
        this.value = value;
    }

    public static GithubId create(String value) {
        validateGithubId(value);

        return new GithubId(value);
    }

    private static void validateGithubId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GitHub ID는 비어 있을 수 없습니다.");
        }
    }
}
