package com.slack.bot.domain.project;

import com.slack.bot.domain.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    private String name;
    private String apiKey;
    private Long userId;

    public static Project create(String name, String apiKey, Long userId) {
        validateName(name);
        validateApiKey(apiKey);
        validateUserId(userId);

        return new Project(name, apiKey, userId);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("프로젝트 이름은 비어 있을 수 없습니다.");
        }
    }

    private static void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("api key는 비어 있을 수 없습니다.");
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("해당 프로젝트를 생성한 회원의 식별자는 비어 있을 수 없습니다.");
        }
    }

    private Project(String name, String apiKey, Long userId) {
        this.name = name;
        this.apiKey = apiKey;
        this.userId = userId;
    }

    public void changeName(String changedName) {
        validateName(changedName);

        this.name = changedName;
    }

    public void changeApiKey(String changedApiKey) {
        validateApiKey(changedApiKey);

        this.apiKey = changedApiKey;
    }
}
