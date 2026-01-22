package com.slack.bot.domain.oauth;

import com.slack.bot.domain.common.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "oauth_verification_states")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthVerificationState extends CreatedAtEntity {

    private Long userId;
    private String state;
    private LocalDateTime expiresAt;

    public static OauthVerificationState create(Long userId, String state, LocalDateTime expiresAt) {
        validateUserId(userId);
        validateState(state);
        validateExpiresAt(expiresAt);

        return new OauthVerificationState(state, userId, expiresAt);
    }

    private static void validateState(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state는 비어 있을 수 없습니다.");
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("회원 ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validateExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("만료 시간은 비어 있을 수 없습니다.");
        }
    }

    private OauthVerificationState(String state, Long userId, LocalDateTime expiresAt) {
        this.state = state;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now) || expiresAt.isEqual(now);
    }
}


